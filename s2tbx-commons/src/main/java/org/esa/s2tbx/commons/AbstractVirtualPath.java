package org.esa.s2tbx.commons;

import com.bc.ceres.core.VirtualDir;
import org.esa.s2tbx.dataio.VirtualDirEx;
import org.esa.snap.utils.FileHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by jcoravu on 9/4/2019.
 */
public abstract class AbstractVirtualPath extends VirtualDir {

    private static final Logger logger = Logger.getLogger(AbstractVirtualPath.class.getName());

    private final boolean copyFilesOnLocalDisk;

    private File localTempDir;

    protected AbstractVirtualPath(boolean copyFilesOnLocalDisk) {
        this.copyFilesOnLocalDisk = copyFilesOnLocalDisk;
    }

    public abstract Path buildPath(String first, String... more);

    public abstract String getFileSystemSeparator();

    public abstract Path getFileIgnoreCaseIfExists(String relativePath) throws IOException;

    public abstract FilePathInputStream getInputStreamIgnoreCaseIfExists(String relativePath) throws IOException;

    public abstract FilePathInputStream getInputStream(String path) throws IOException;

    public abstract FilePath getFilePath(String childRelativePath) throws IOException;

    @Override
    public void close() {
        cleanup();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        cleanup();
    }

    @Override
    public File getTempDir() throws IOException {
        return this.localTempDir;
    }

    protected final Path copyFileOnLocalDiskIfNeeded(Path entryPath, String childRelativePath) throws IOException {
        if (this.copyFilesOnLocalDisk && Files.isRegularFile(entryPath)) {
            // copy the file from the zip archive on the local disk
            if (this.localTempDir == null) {
                this.localTempDir = VirtualDir.createUniqueTempDir();
            }
            Path localFilePath = this.localTempDir.toPath().resolve(childRelativePath);
            if (FileHelper.canCopyOrReplaceFile(entryPath, localFilePath)) {
                Path parentFolder = localFilePath.getParent();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Copy file '" + entryPath.toString() + "' to local folder '" + parentFolder.toString() + "'.");
                }

                if (!Files.exists(parentFolder)) {
                    Files.createDirectories(parentFolder);
                }
                FileHelper.copyFileUsingInputStream(entryPath, localFilePath.toString(), VirtualDirEx.BUFFER_SIZE);
            }
            return localFilePath;
        } else {
            // do not copy the file from the zip archive on the local disk
            return entryPath;
        }
    }

    private void cleanup() {
        if (this.localTempDir != null) {
            deleteFileTree(this.localTempDir);
            this.localTempDir = null;
        }
    }

    public static String replaceFileSeparator(String path, String fileSystemSeparator) {
        return path.replace("\\", fileSystemSeparator).replace("/", fileSystemSeparator);
    }

    protected static class FindChildFileVisitor extends SimpleFileVisitor<Path> {

        private final Path childPathToFind;

        private Path existingChildPath;

        public FindChildFileVisitor(Path childPathToFind) {
            this.childPathToFind = childPathToFind;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return checkItem(dir);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            return checkItem(file);
        }

        private FileVisitResult checkItem(Path currentPath) {
            if (currentPath.toString().equalsIgnoreCase(this.childPathToFind.toString())) {
                this.existingChildPath = currentPath;
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        Path getExistingChildPath() {
            return this.existingChildPath;
        }
    }

    protected static class ListAllFilesVisitor extends SimpleFileVisitor<Path> {

        private final TreeSet<String> nameSet;

        public ListAllFilesVisitor() {
            this.nameSet = new TreeSet<>();
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            this.nameSet.add(file.toString());
            return FileVisitResult.CONTINUE;
        }

        TreeSet<String> getNameSet() {
            return this.nameSet;
        }
    }

}
