package cn.com.zoweesw.usbdevices;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by Mr.zhou.<br/>
 * Describe：
 */

public class SDUtils {

    private static final String TAG = "文件工具类";

    public static String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED); //判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();//获取目录
//            sdDir1 = Environment.getDataDirectory();
//            sdDir2 = Environment.getRootDirectory();
        } else {
            Log.d(TAG, "getSDPath: sd卡不存在");
        }
        Log.d(TAG, "getSDPath: " + sdDir.getAbsolutePath());
        return sdDir.getAbsolutePath();
    }

    public static String getUSBPath() {
        String usbDir=null;
        File storage = new File("/storage");
        File[] files = storage.listFiles();
        for (final File file : files) {
            if (file.canRead()) {
                if (!file.getName().equals(Environment.getExternalStorageDirectory().getName())) { //满足该条件的文件夹就是u盘在手机上的目录 } }
                  usbDir=file.getAbsoluteFile().toString();
                }
            }
        }
        return usbDir;
    }
}