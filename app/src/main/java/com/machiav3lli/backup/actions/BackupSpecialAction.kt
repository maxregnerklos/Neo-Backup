package com.machiav3lli.backup.actions;

import android.content.Context;
import com.machiav3lli.backup.dbs.entity.SpecialInfo;
import com.machiav3lli.backup.handler.LogsHandler;
import com.machiav3lli.backup.handler.ShellHandler;
import com.machiav3lli.backup.handler.ShellHandler.ShellCommandFailedException;
import com.machiav3lli.backup.items.ActionResult;
import com.machiav3lli.backup.items.Package;
import com.machiav3lli.backup.items.StorageFile;
import com.machiav3lli.backup.tasks.AppActionWork;
import com.machiav3lli.backup.utils.CryptoSetupException;
import timber.log.Timber;

import java.io.File;
import java.util.List;

public class BackupSpecialAction extends BackupAppAction {
    public BackupSpecialAction(Context context, AppActionWork work, ShellHandler shell) {
        super(context, work, shell);
    }

    @Override
    public ActionResult run(Package app, int backupMode) {
        if ((backupMode & MODE_APK) == MODE_APK) {
            Timber.e("Special contents don't have APKs to backup. Ignoring");
        }
        return (backupMode & MODE_DATA) == MODE_DATA
                ? super.run(app, MODE_DATA)
                : new ActionResult(app, null,
                "Special backup only backups data, but data was not selected for backup", false);
    }

    @Override
    protected boolean backupData(Package app, StorageFile backupInstanceDir, byte[] iv)
            throws BackupFailedException, CryptoSetupException {
        Timber.i("%s: Backup special data", app);
        if (!(app.getPackageInfo() instanceof SpecialInfo)) {
            throw new IllegalArgumentException("Provided app is not an instance of SpecialAppMetaInfo");
        }
        SpecialInfo appInfo = (SpecialInfo) app.getPackageInfo();
        List<String> specialFiles = appInfo.getSpecialFiles();
        // Get file list
        // This can be optimized, because it's known, that special backups won't meet any symlinks
        // since the list of files is fixed
        // It would make sense to implement something like TarUtils.addFilepath with SuFileInputStream and
        List<ShellHandler.FileInfo> filesToBackup = null;
        try {
            for (String filePath : specialFiles) {
                File file = new File(filePath);
                boolean isDirSource = filePath.endsWith("/");
                List<ShellHandler.FileInfo> fileInfos;
                if (isDirSource) {
                    // directory
                    try {
                        // add contents
                        fileInfos = shell.suGetDetailedDirectoryContents(
                                filePath.substring(0, filePath.length() - 1), isDirSource, file.getName());
                    } catch (ShellCommandFailedException e) {
                        LogsHandler.unexpectedException(e);
                        continue; //TODO hg42: avoid checking the error message text for now
                        //TODO hg42: alternative implementation, better replaced this by API, when root permissions available, e.g. via Shizuku
                        //    if(e.shellResult.err.toString().contains("No such file or directory", ignoreCase = true))
                        //        continue
                        //    throw(e)
                    }
                    // add directory itself
                    filesToBackup.add(shell.suGetFileInfo(file.getAbsolutePath()));
                } else {
                    // regular file
                    filesToBackup.add(shell.suGetFileInfo(file.getAbsolutePath()));
                }
                filesToBackup.addAll(fileInfos);
            }
            genericBackupData(BACKUP_DIR_DATA, backupInstanceDir, filesToBackup, true, iv);
        } catch (RuntimeException e) {
            throw new BackupFailedException(e.getMessage(), e);
        } catch (ShellCommandFailedException e) {
            String error = extractErrorMessage(e.shellResult);
            Timber.e("%s: Backup Special Data failed: %s", app, error);
            throw new BackupFailedException(error, e);
        } catch (Throwable e) {
            LogsHandler.unexpectedException(e, app);
            throw new BackupFailedException("unhandled exception", e);
        }
        for (String filePath : specialFiles) {
            new File(filePath).delete();
        }
        return true;
    }

    // Stubbing some functions, to avoid executing them with potentially dangerous results
    @Override
    protected void backupPackage(Package app, StorageFile backupInstanceDir) {
        // stub
    }

    @Override
    protected boolean backupDeviceProtectedData(Package app, StorageFile backupInstanceDir, byte[] iv) {
        // stub
        return false;
    }

    @Override
    protected boolean backupExternalData(Package app, StorageFile backupInstanceDir, byte[] iv) {
        // stub
        return false;
    }

    @Override
    protected boolean backupObbData(Package app, StorageFile backupInstanceDir, byte[] iv) {
        // stub
        return false;
    }

    @Override
    protected void preprocessPackage(String type, String packageName) {
        // stub
    }

    @Override
    protected void postprocessPackage(String type, String packageName) {
        // stub
    }
}
