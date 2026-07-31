package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class WineInstaller {
    private static final String WINE_10_10_CONTAINER_PATTERN_ASSET = "wine/container_pattern-10.10.tzst";
    private static final String[][] VERIFIED_WINE_10_10_FILES = new String[][]{
            {"bin/wine", "1D21E085E2FEBB15F3BE3F6E51459CF5E6C543ABECE9B463773CC03EEDAB2263"},
            {"bin/wineserver", "F91A57493829473F3C08DC4F8976A47646B7DF736C352C0359E26F2C1CB9784F"},
            {"lib/wine/x86_64-unix/ntdll.so", "39F254917B939051AB32FE2DF6357ACC47B22B39E0C62DCE4766E052286BDB0F"}
    };

    public static void generateWineprefix(WineInfo wineInfo, XEnvironment environment) {
        Activity activity = (Activity)environment.getContext();
        RootFS rootFS = environment.getRootFS();
        final File installedWineDir = rootFS.getInstalledWineDir();
        rootFS.setWinePath(wineInfo.path);
        final File rootDir = rootFS.getRootDir();

        final File containerPatternDir = new File(installedWineDir, "/preinstall/container-pattern");
        if (containerPatternDir.isDirectory()) FileUtils.delete(containerPatternDir);
        containerPatternDir.mkdirs();

        File linkFile = new File(rootDir, RootFS.HOME_PATH);
        FileUtils.symlink(containerPatternDir.getPath(), linkFile.getPath());

        GuestProgramLauncherComponent guestProgramLauncherComponent = environment.getComponent(GuestProgramLauncherComponent.class);
        guestProgramLauncherComponent.setBox64Preset(Box64Preset.STABILITY);
        guestProgramLauncherComponent.setGuestExecutable("wine explorer /desktop=shell,"+ Container.DEFAULT_SCREEN_SIZE+" C:\\windows\\system32\\winecfg.exe");

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            if (status > 0) {
                failWineInstallation(activity, installedWineDir, preloaderDialog);
                return;
            }

            preloaderDialog.showOnUiThread(R.string.finishing_installation);
            guestProgramLauncherComponent.setGuestExecutable("wineserver -k -w");
            guestProgramLauncherComponent.setTerminationCallback((serverStatus) -> Executors.newSingleThreadExecutor().execute(() -> {
                if (serverStatus > 0 || !finishWineInstallation(rootFS, wineInfo)) {
                    failWineInstallation(activity, installedWineDir, preloaderDialog);
                    return;
                }

                preloaderDialog.closeOnUiThread();
                restartInSettings(activity);
            }));
            guestProgramLauncherComponent.start();
        });
    }

    public static boolean isVerifiedWine10_10(WineInfo wineInfo) {
        if (!wineInfo.fullVersion().equals("10.10")) return false;
        File wineDir = new File(wineInfo.path);
        for (String[] fileInfo : VERIFIED_WINE_10_10_FILES) {
            if (!sha256(new File(wineDir, fileInfo[0])).equals(fileInfo[1])) return false;
        }
        return true;
    }

    private static String sha256(File file) {
        if (!file.isFile()) return "";
        try (InputStream inputStream = new java.io.FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            int count;
            while ((count = inputStream.read(buffer)) != -1) digest.update(buffer, 0, count);

            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02X", value));
            return result.toString();
        }
        catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static void installVerifiedWine10_10Async(Context context, WineInfo wineInfo, Callback<Boolean> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            RootFS rootFS = RootFS.find(context);
            boolean success = isVerifiedWine10_10(wineInfo) && installVerifiedWine10_10(context, rootFS, wineInfo);
            if (!success) FileUtils.delete(new File(rootFS.getInstalledWineDir(), "/preinstall"));
            if (callback != null) callback.call(success);
        });
    }

    private static boolean installVerifiedWine10_10(Context context, RootFS rootFS, WineInfo wineInfo) {
        File installedWineDir = rootFS.getInstalledWineDir();
        File preinstallDir = new File(installedWineDir, "/preinstall");
        File tempContainerPattern = new File(preinstallDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");
        File installedContainerPattern = new File(installedWineDir, tempContainerPattern.getName());
        File sourceWineDir = new File(wineInfo.path);
        File installedWineVersionDir = new File(installedWineDir, wineInfo.identifier());

        if (!copyAsset(context, WINE_10_10_CONTAINER_PATTERN_ASSET, tempContainerPattern) ||
                !isValidContainerPattern(tempContainerPattern)) return false;
        if (!tempContainerPattern.renameTo(installedContainerPattern)) return false;
        if (!sourceWineDir.renameTo(installedWineVersionDir)) {
            installedContainerPattern.delete();
            return false;
        }

        FileUtils.delete(preinstallDir);
        return true;
    }

    private static boolean copyAsset(Context context, String assetPath, File destination) {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
        try (InputStream inputStream = context.getAssets().open(assetPath);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destination)) {
            if (!StreamUtils.copy(inputStream, outputStream)) {
                destination.delete();
                return false;
            }
            return true;
        }
        catch (IOException e) {
            destination.delete();
            return false;
        }
    }

    private static boolean isValidContainerPattern(File containerPattern) {
        byte[] systemRegistry = TarCompressorUtils.read(TarCompressorUtils.Type.ZSTD, containerPattern, "*.wine/system.reg");
        byte[] userRegistry = TarCompressorUtils.read(TarCompressorUtils.Type.ZSTD, containerPattern, "*.wine/user.reg");
        return isValidWin64Registry(systemRegistry) && isValidWin64Registry(userRegistry);
    }

    private static boolean finishWineInstallation(RootFS rootFS, WineInfo wineInfo) {
        File rootDir = rootFS.getRootDir();
        File installedWineDir = rootFS.getInstalledWineDir();
        File wineprefix = new File(rootDir, RootFS.WINEPREFIX);
        if (!isValidWin64Wineprefix(wineprefix)) return false;

        FileUtils.delete(new File(wineprefix, ".wineserver"));
        FileUtils.writeString(new File(wineprefix, ".update-timestamp"), "disable\n");

        File userDir = new File(wineprefix, "drive_c/users/xuser");
        File[] userFiles = userDir.listFiles();
        if (userFiles != null) {
            for (File userFile : userFiles) {
                if (FileUtils.isSymlink(userFile)) {
                    String path = userFile.getPath();
                    userFile.delete();
                    (new File(path)).mkdirs();
                }
            }
        }

        File containerPatternFile = new File(installedWineDir, "/preinstall/container-pattern-"+wineInfo.fullVersion()+".tzst");
        if (!TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, wineprefix, containerPatternFile, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL) ||
                !containerPatternFile.isFile() || containerPatternFile.length() == 0) return false;

        File installedContainerPatternFile = new File(installedWineDir, containerPatternFile.getName());
        File sourceWineDir = new File(wineInfo.path);
        File installedWineVersionDir = new File(installedWineDir, wineInfo.identifier());

        if (!containerPatternFile.renameTo(installedContainerPatternFile)) return false;
        if (!sourceWineDir.renameTo(installedWineVersionDir)) {
            installedContainerPatternFile.delete();
            return false;
        }

        FileUtils.delete(new File(installedWineDir, "/preinstall"));
        return true;
    }

    private static boolean isValidWin64Wineprefix(File wineprefix) {
        return isValidWin64Registry(new File(wineprefix, "system.reg")) &&
                isValidWin64Registry(new File(wineprefix, "user.reg"));
    }

    private static boolean isValidWin64Registry(File registryFile) {
        if (!registryFile.isFile() || registryFile.length() == 0) return false;

        boolean validHeader = false;
        boolean win64Architecture = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(registryFile))) {
            String line;
            for (int index = 0; index < 32 && (line = reader.readLine()) != null; index++) {
                if (index == 0) validHeader = line.equals("WINE REGISTRY Version 2");
                if (line.equals("#arch=win64")) win64Architecture = true;
            }
        }
        catch (IOException e) {
            return false;
        }
        return validHeader && win64Architecture;
    }

    private static boolean isValidWin64Registry(byte[] registryData) {
        if (registryData == null || registryData.length == 0) return false;
        String registry = new String(registryData, java.nio.charset.StandardCharsets.UTF_8);
        return registry.startsWith("WINE REGISTRY Version 2") && registry.contains("\n#arch=win64\n");
    }

    private static void failWineInstallation(Activity activity, File installedWineDir, PreloaderDialog preloaderDialog) {
        FileUtils.delete(new File(installedWineDir, "/preinstall"));
        preloaderDialog.closeOnUiThread();
        AppUtils.showToast(activity, R.string.unable_to_install_wine);
        restartInSettings(activity);
    }

    private static void restartInSettings(Activity activity) {
        AppUtils.RestartApplicationOptions options = new AppUtils.RestartApplicationOptions();
        options.selectedMenuItemId = R.id.menu_item_settings;
        AppUtils.restartApplication(activity, options);
    }

    public static void extractWineFileForInstallAsync(Context context, Uri uri, Callback<File> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            File destination = new File(RootFS.find(context).getInstalledWineDir(), "/preinstall/wine");
            FileUtils.delete(destination);
            destination.mkdirs();
            boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, uri, destination);
            if (!success) FileUtils.delete(destination);
            if (callback != null) callback.call(success ? destination : null);
        });
    }

    public static void findWineVersionAsync(Context context, File wineDir, Callback<WineInfo> callback) {
        if (wineDir == null || !wineDir.isDirectory()) {
            callback.call(null);
            return;
        }
        File[] files = wineDir.listFiles();
        if (files == null || files.length == 0) {
            callback.call(null);
            return;
        }

        for (int depth = 0; depth < 8 && files.length == 1 && files[0].isDirectory(); depth++) {
            wineDir = files[0];
            files = wineDir.listFiles();
            if (files == null || files.length == 0) {
                callback.call(null);
                return;
            }
        }

        File binDir = null;
        for (File file : files) {
            if (file.isDirectory() && file.getName().equals("bin")) {
                binDir = file;
                break;
            }
        }

        if (binDir == null) {
            callback.call(null);
            return;
        }

        File wineBin = new File(binDir, "wine");
        File wineBin64 = new File(binDir, "wine64");

        if (!wineBin.isFile()) {
            callback.call(null);
            return;
        }

        final boolean useWineBin64 = wineBin64.isFile() && ElfHelper.is64Bit(wineBin64);
        final boolean is64Bit = useWineBin64 || ElfHelper.is64Bit(wineBin);
        if (!is64Bit) {
            callback.call(null);
            return;
        }

        RootFS rootFS = RootFS.find(context);
        File rootDir = rootFS.getRootDir();
        String wineBinPath = useWineBin64 ? wineBin64.getPath() : wineBin.getPath();
        final String winePath = wineDir.getPath();

        final AtomicReference<WineInfo> wineInfoRef = new AtomicReference<>();
        Callback<String> debugCallback = (line) -> {
            Pattern pattern = Pattern.compile("^wine\\-([0-9\\.]+)\\-?([0-9\\.]+)?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String version = matcher.group(1);
                String subversion = matcher.groupCount() >= 2 ? matcher.group(2) : null;
                wineInfoRef.set(new WineInfo(version, subversion, winePath));
            }
        };

        ProcessHelper.addDebugCallback(debugCallback);

        File linkFile = new File(rootDir, RootFS.HOME_PATH);
        linkFile.delete();
        FileUtils.symlink(wineDir, linkFile);

        XEnvironment environment = new XEnvironment(context, rootFS);
        GuestProgramLauncherComponent guestProgramLauncherComponent = new GuestProgramLauncherComponent();
        guestProgramLauncherComponent.setGuestExecutable(wineBinPath+" --version");
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            callback.call(wineInfoRef.get());
            ProcessHelper.removeDebugCallback(debugCallback);
        });
        environment.addComponent(guestProgramLauncherComponent);
        environment.startEnvironmentComponents();
    }

    public static ArrayList<WineInfo> getInstalledWineInfos(Context context) {
        ArrayList<WineInfo> wineInfos = new ArrayList<>();
        wineInfos.add(WineInfo.MAIN_WINE_INFO);
        File installedWineDir = RootFS.find(context).getInstalledWineDir();

        File[] files = installedWineDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("wine")) wineInfos.add(WineInfo.fromIdentifier(context, name));
            }
        }

        return wineInfos;
    }
}
