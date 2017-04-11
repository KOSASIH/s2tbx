package org.esa.s2tbx.dataio.gdal.activator;

import org.esa.s2tbx.dataio.gdal.GDALInstaller;
import org.esa.s2tbx.dataio.gdal.GDALUtils;
import org.esa.s2tbx.dataio.gdal.GdalInstallInfo;
import org.esa.s2tbx.jni.EnvironmentVariables;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.utils.NativeLibraryUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jean Coravu
 */
public class GDALDistributionInstaller {
    private static final Logger logger = Logger.getLogger(GDALDistributionInstaller.class.getName());

    private GDALDistributionInstaller() {
    }

    /**
     * Install the GDAL library if missing.
     *
     * @throws IOException
     */
    public static void install() throws IOException {
        // install the GDAL library from the distribution
        GDALInstaller.OSCategory osCategory = GDALInstaller.OSCategory.getOSCategory();
        if (osCategory.getDirectory() == null) {
            logger.log(Level.INFO, "No distribution folder found on " + osCategory.getOperatingSystemName() + " operation system.");
            return;
        }
        if (osCategory.getZipFileName() == null) {
            logger.log(Level.INFO, "No library zip file name found on " + osCategory.getOperatingSystemName() + " operation system.");
            return;
        }

        logger.log(Level.INFO, "Install the GDAL library from the distribution on " + osCategory.getOperatingSystemName() + " operation system.");

        Path gdalApplicationFolderPath = SystemUtils.getAuxDataPath().resolve("gdal");
        if (gdalApplicationFolderPath == null) {
            logger.log(Level.INFO, "No folder path to install the GDAL integration on the local disk.");
            return;
        }

        GDALInstaller installer = new GDALInstaller();
        Path gdalDistributionRootFolderPath = installer.copyDistribution(gdalApplicationFolderPath, osCategory);

        if (org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS) {
            processInstalledWindowsDistribution(gdalDistributionRootFolderPath);
            GdalInstallInfo.INSTANCE.setLocations(gdalDistributionRootFolderPath);
            GDALUtils.initDrivers();
        } else if (org.apache.commons.lang.SystemUtils.IS_OS_LINUX) {
            String currentDirectoryNative = EnvironmentVariables.getCurrentDirectory();
            try {
                processInstalledLinuxDistribution(gdalDistributionRootFolderPath);
                GdalInstallInfo.INSTANCE.setLocations(gdalDistributionRootFolderPath);
                GDALUtils.initDrivers();
            } finally {
                EnvironmentVariables.changeCurrentDirectory(currentDirectoryNative);
            }
        }
    }

    private static void processInstalledLinuxDistribution(Path gdalDistributionRootFolderPath) throws IOException {
        Path nativeFolderPath = gdalDistributionRootFolderPath.resolve("lib/jni");
        NativeLibraryUtils.registerNativePaths(nativeFolderPath);

        EnvironmentVariables.changeCurrentDirectory(gdalDistributionRootFolderPath.resolve("lib/").toString());

        Path gdalDataFolderPath = gdalDistributionRootFolderPath.resolve("share/gdal");
        StringBuilder gdalDataValue = new StringBuilder();
        gdalDataValue.append("GDAL_DATA")
                .append("=")
                .append(gdalDataFolderPath.toString());
        EnvironmentVariables.setEnvironmentVariable(gdalDataValue.toString());
    }

    private static void processInstalledWindowsDistribution(Path gdalDistributionRootFolderPath) throws IOException {
        Path gdalBinFolderPath = gdalDistributionRootFolderPath.resolve("bin");

        Path nativeFolderPath = gdalBinFolderPath.resolve("gdal/java");
        NativeLibraryUtils.registerNativePaths(nativeFolderPath);

        Path gdalAppsFolderPath = gdalBinFolderPath.resolve("gdal/apps");

        String pathEnvironment = EnvironmentVariables.getEnvironmentVariable("PATH");
        boolean foundBinFolderInPath = findFolderInPathEnvironment(gdalBinFolderPath, pathEnvironment);
        if (!foundBinFolderInPath) {
            StringBuilder newPathValue = new StringBuilder();
            newPathValue.append("PATH")
                    .append("=")
                    .append(gdalBinFolderPath.toString())
                    .append(File.pathSeparator)
                    .append(gdalAppsFolderPath.toString())
                    .append(File.pathSeparator)
                    .append(pathEnvironment);
            EnvironmentVariables.setEnvironmentVariable(newPathValue.toString());
        }

        Path gdalDataFolderPath = gdalBinFolderPath.resolve("gdal-data");
        StringBuilder gdalDataValue = new StringBuilder();
        gdalDataValue.append("GDAL_DATA")
                .append("=")
                .append(gdalDataFolderPath.toString());
        EnvironmentVariables.setEnvironmentVariable(gdalDataValue.toString());

        Path gdalDriverFolderPath = gdalBinFolderPath.resolve("gdal/plugins");
        StringBuilder gdalDriverValue = new StringBuilder();
        gdalDriverValue.append("GDAL_DRIVER_PATH")
                .append("=")
                .append(gdalDriverFolderPath.toString());
        EnvironmentVariables.setEnvironmentVariable(gdalDriverValue.toString());
    }

    private static boolean findFolderInPathEnvironment(Path folderPathToCheck, String pathEnvironment) throws IOException {
        String fullFolderPath = folderPathToCheck.toFile().getCanonicalPath();
        boolean foundFolderInPath = false;
        StringTokenizer str = new StringTokenizer(pathEnvironment, File.pathSeparator);
        while (str.hasMoreTokens() && !foundFolderInPath) {
            String currentFolderPathAsString = str.nextToken();
            Path currentFolderPath = Paths.get(currentFolderPathAsString);
            String currentFullFolderPath = currentFolderPath.toFile().getCanonicalPath();
            if (currentFullFolderPath.equals(fullFolderPath)) {
                foundFolderInPath = true;
            }
        }
        return foundFolderInPath;
    }
}
