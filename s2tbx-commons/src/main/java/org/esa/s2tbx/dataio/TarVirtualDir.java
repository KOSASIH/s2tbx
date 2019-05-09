package org.esa.s2tbx.dataio;

import com.bc.ceres.core.VirtualDir;
import org.esa.s2tbx.commons.FilePathInputStream;
import org.esa.snap.core.util.io.FileUtils;
import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarHeader;
import org.xeustechnologies.jtar.TarInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Private implementation of a virtual directory representing the contents of a tar file.
 */
class TarVirtualDir extends VirtualDirEx {

    public static final byte LF_SPEC_LINK = (byte) 'L';

    private final Path archiveFile;
    private File extractDir;
    private FutureTask<Void> unpackTask;
    private ExecutorService executor;
    private boolean unpackStarted = false;

    private class UnpackProcess implements Callable<Void> {
        @Override
        public Void call() throws Exception {
            ensureUnpacked();
            return null;
        }
    }

    public TarVirtualDir(Path tgz) {
        if (tgz == null) {
            throw new NullPointerException("Input file shall not be null");
        }
        archiveFile = tgz;
        extractDir = null;
        unpackTask = new FutureTask<>(new TarVirtualDir.UnpackProcess());
        executor = Executors.newSingleThreadExecutor();
    }

    public static String getFilenameFromPath(String path) {
        int lastSepIndex = path.lastIndexOf("/");
        if (lastSepIndex == -1) {
            lastSepIndex = path.lastIndexOf("\\");
            if (lastSepIndex == -1) {
                return path;
            }
        }

        return path.substring(lastSepIndex + 1, path.length());
    }

    public static boolean isTgz(String filename) {
        final String extension = FileUtils.getExtension(filename);
        return (".tgz".equals(extension) || ".gz".equals(extension));
    }

    public static boolean isTar(String filename) {
        return ".tar".equals(FileUtils.getExtension(filename));
    }

    @Override
    public String getBasePath() {
        return archiveFile.toFile().getPath();
    }

    @Override
    public File getBaseFile() {
        return archiveFile.toFile();
    }

    @Override
    public Path buildPath(String first, String... more) {
        FileSystem fileSystem = this.archiveFile.getFileSystem();
        return fileSystem.getPath(first, more);
    }

    @Override
    public String getFileSystemSeparator() {
        FileSystem fileSystem = this.archiveFile.getFileSystem();
        return fileSystem.getSeparator();
    }

    @Override
    public FilePathInputStream getInputStream(String path) throws IOException {
        Path file = getFile(path).toPath();
        InputStream inputStream = Files.newInputStream(file);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        return new FilePathInputStream(file, bufferedInputStream, null);
    }

    @Override
    public File getFile(String path) throws IOException {
        ensureUnpackedStarted();
        try {
            while (!unpackTask.isDone()) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            // swallowed exception
        } finally {
            executor.shutdown();
        }
        final File file = new File(extractDir, path);
        if (!(file.isFile() || file.isDirectory())) {
            throw new FileNotFoundException("The path '"+path+"' does not exist in the folder '"+extractDir.getAbsolutePath()+"'.");
        }
        return file;
    }

    @Override
    public String[] list(String path) throws IOException {
        final File file = getFile(path);
        return file.list();
    }

    public boolean exists(String s) {
        return Files.exists(this.archiveFile);
    }

    @Override
    public void close() {
        if (this.extractDir != null) {
            FileUtils.deleteTree(this.extractDir);
            this.extractDir = null;
        }
    }

    @Override
    public boolean isCompressed() {
        return isTgz(archiveFile.getFileName().toString());
    }

    @Override
    public boolean isArchive() {
        return isTgz(archiveFile.getFileName().toString());
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public File getTempDir() throws IOException {
        ensureUnpackedStarted();
        return extractDir;
    }

    public void ensureUnpacked() throws IOException {
        ensureUnpacked(null);
    }

    public void ensureUnpacked(File unpackFolder) throws IOException {
        if (this.extractDir == null) {
            this.extractDir = unpackFolder != null ? unpackFolder : VirtualDir.createUniqueTempDir();
            TarInputStream tis = null;
            OutputStream outStream = null;
            try (InputStream inputStream = Files.newInputStream(this.archiveFile);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {

                if (isTgz(this.archiveFile.getFileName().toString())) {
                    tis = new TarInputStream(new GZIPInputStream(bufferedInputStream));
                } else {
                    tis = new TarInputStream(bufferedInputStream);
                }
                TarEntry entry;

                String longLink = null;
                while ((entry = tis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    boolean entryIsLink = entry.getHeader().linkFlag == TarHeader.LF_LINK || entry.getHeader().linkFlag == LF_SPEC_LINK;
                    if (longLink != null && longLink.startsWith(entryName)) {
                        entryName = longLink;
                        longLink = null;
                    }
                    if (entry.isDirectory()) {
                        final File directory = new File(this.extractDir, entryName);
                        ensureDirectory(directory);
                        continue;
                    }

                    final String fileNameFromPath = getFilenameFromPath(entryName);
                    final int pathIndex = entryName.indexOf(fileNameFromPath);
                    String tarPath = null;
                    if (pathIndex > 0) {
                        tarPath = entryName.substring(0, pathIndex - 1);
                    }

                    File targetDir;
                    if (tarPath != null) {
                        targetDir = new File(this.extractDir, tarPath);
                    } else {
                        targetDir = this.extractDir;
                    }

                    ensureDirectory(targetDir);
                    final File targetFile = new File(targetDir, fileNameFromPath);
                    if (!entryIsLink && targetFile.isFile()) {
                        continue;
                    }

                    if (!entryIsLink && !targetFile.createNewFile()) {
                        throw new IOException("Unable to create file: " + targetFile.getAbsolutePath());
                    }

                    outStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                    final byte data[] = new byte[1024 * 1024];
                    int count;
                    while ((count = tis.read(data)) != -1) {
                        outStream.write(data, 0, count);
                        //if the entry is a link, must be saved, since the name of the next entry depends on this
                        if (entryIsLink) {
                            longLink = (longLink == null ? "" : longLink) + new String(data, 0, count);
                        } else {
                            longLink = null;
                        }
                    }
                    //the last character is \u0000, so it must be removed
                    if (longLink != null) {
                        longLink = longLink.substring(0, longLink.length() - 1);
                    }
                    outStream.flush();
                    outStream.close();

                }
            } finally {
                if (tis != null) {
                    tis.close();
                }
                if (outStream != null) {
                    outStream.flush();
                    outStream.close();
                }
            }
        }
    }

    private void ensureDirectory(File targetDir) throws IOException {
        if (!targetDir.isDirectory()) {
            if (!targetDir.mkdirs()) {
                throw new IOException("unable to create directory: " + targetDir.getAbsolutePath());
            }
        }
    }

    @Override
    public String[] listAll(Pattern...patterns) {
        List<String> fileNames = new ArrayList<>();
        TarInputStream tis;
        try (InputStream inputStream = Files.newInputStream(this.archiveFile);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {

            if (isTgz(this.archiveFile.getFileName().toString())) {
                tis = new TarInputStream(new GZIPInputStream(bufferedInputStream));
            } else {
                tis = new TarInputStream(bufferedInputStream);
            }
            TarEntry entry;
            String longLink = null;
            while ((entry = tis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean entryIsLink = entry.getHeader().linkFlag == TarHeader.LF_LINK || entry.getHeader().linkFlag == LF_SPEC_LINK;
                if (longLink != null && longLink.startsWith(entryName)) {
                    entryName = longLink;
                    longLink = null;
                }
                //if the entry is a link, must be saved, since the name of the next entry depends on this
                if (entryIsLink) {
                    final byte data[] = new byte[1024 * 1024];
                    int count;
                    while ((count = tis.read(data)) != -1) {
                        longLink = (longLink == null ? "" : longLink) + new String(data, 0, count);
                    }
                } else {
                    longLink = null;
                    fileNames.add(entryName);
                }
                //the last character is \u0000, so it must be removed
                if (longLink != null) {
                    longLink = longLink.substring(0, longLink.length() - 1);
                }
            }
        } catch (IOException e) {
            // cannot open/read tar, list will be empty
            fileNames = new ArrayList<>();
        }
        return fileNames.toArray(new String[fileNames.size()]);
    }

    public void ensureUnpackedStarted() {
        if (!unpackStarted) {
            unpackStarted = true;
            executor.execute(unpackTask);
        }
    }
}
