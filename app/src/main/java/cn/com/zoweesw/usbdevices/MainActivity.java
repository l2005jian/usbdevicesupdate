package cn.com.zoweesw.usbdevices;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Parcelable;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.mjdev.libaums.UsbMassStorageDevice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final String TAG = "MainActivity";
    private ExecutorService executorService;
    private TextView main_tv_msg;
    private Button mUsbToSd_Button;
    private Button msdToUsb_Button;
    private Button mStopWrite;
    private Button mStopRead;
    private ScrollView main_sv;
    //    private long startTime;
    private int BUFFER_SIZE = 1024*1024*130;
    //    private String sdPath;
//    private String usbPath;
    private List<DiskInfo> mDiskList = new ArrayList<DiskInfo>();
    private UsbMassStorageDevice[] storageDevices;
    private String usbpktmp;
    private String sdpktmp;
    //    private String res;
    volatile private boolean wirteFlag;
    volatile private boolean readFlag;
    long averlength = 0;
    private long sta;
    private long stt;
    private int fileSize = 1;
    private boolean isCreateFile = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        registerReceiver(); //监听OTG设备
        getDiskInfo();
        initview();
        bindView();
    }

    private void getFile() {
        File[] files = getExternalFilesDirs(null);
        File usbFile = null;
        for (int i = 0; i < files.length; i++) {
            if (files[i].getPath().indexOf("emulated") >= 0) {
                ilog(files[0].getPath().toString());
                sdpktmp = files[0].getPath().toString();
                ilog(sdpktmp);
                continue;
            } else {
                usbFile = files[i];
                ilog(files[i].toString());
                break;
            }

        }
        if (usbFile != null) {
            ilog(usbFile.getPath());
            usbpktmp = usbFile.getPath();
        } else {
            //ilog("no usb devices");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        finish();
    }

    private void getDiskInfo() {
        StorageManager mstorageManager = (StorageManager) this.getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
        try {
            Method methodGetDisks = StorageManager.class.getMethod("getDisks");
            Method methodGetStorageVolumes = StorageManager.class.getMethod("getVolumeList");
            Method getVolumeById = StorageManager.class.getMethod("findVolumeById", String.class);

            StorageVolume[] storageVolumes = (StorageVolume[]) methodGetStorageVolumes.invoke(mstorageManager);
            List disks = (List) methodGetDisks.invoke(mstorageManager);

            //DiskInfo
            Class<?> diskIndoClass = Class.forName("android.os.storage.DiskInfo");
            Method mGetDiskId = diskIndoClass.getMethod("getId");
            Field diskName = diskIndoClass.getField("label");

            //StorageVolume
            Class<?> storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
            Method mGetStorageVolId = storageVolumeClass.getMethod("getId");
            Method mGetStorageVolDescription = storageVolumeClass.getMethod("getDescription", Context.class);
            Method mGetStorageVolPath = storageVolumeClass.getMethod("getPath");
            Method isRemovable = storageVolumeClass.getMethod("isRemovable");
            Method getVolumeState = StorageManager.class.getMethod("getVolumeState", String.class);

            //VolumeInfo
            Class<?> volumeClass = Class.forName("android.os.storage.VolumeInfo");
            Method volumeDiskId = volumeClass.getMethod("getDiskId");

            for (int i = 0; i < disks.size(); i++) {
                DiskInfo diskInfo = new DiskInfo();
                Parcelable parcelable = (Parcelable) disks.get(i);
                diskInfo.diskId = (String) mGetDiskId.invoke(parcelable);
                Log.e("test", "diskid : " + diskInfo.diskId);
                String des = (String) diskName.get(parcelable);
                Log.e("test", "diskName : " + des);
                diskInfo.name = des;
                mDiskList.add(diskInfo);
            }

            for (int j = 0; j < storageVolumes.length; j++) {
                DiskPartition partition = new DiskPartition();
                StorageVolume storageVolume = storageVolumes[j];
                partition.partitionId = (String) mGetStorageVolId.invoke(storageVolume);
                if ("emulated".equals(partition.partitionId)) {
                    continue;
                }
                partition.name = (String) mGetStorageVolDescription.invoke(storageVolume, this);
                partition.path = (String) mGetStorageVolPath.invoke(storageVolume);
                Boolean removeAble = ((Boolean) isRemovable.invoke(storageVolume)).booleanValue();
                String state = (String) getVolumeState.invoke(mstorageManager, partition.path);
                if ("mounted".equals(state) && removeAble) {
                    partition.diskId = (String) volumeDiskId.invoke(getVolumeById.invoke(mstorageManager, partition.partitionId));
                    for (DiskInfo diskInfo : mDiskList) {
                        if (diskInfo.diskId.equals(partition.diskId)) {
                            getStorageBlockInfo(partition);
                            diskInfo.diskPartitions.add(partition);
                            Log.e("test", "partition.name : " + partition.name);
                            Log.e("test", "partition.diskId : " + partition.diskId);
                            Log.e("test", "partition.path : " + partition.path);
                            String partitionId = partition.diskId;
                            String[] idSplit = partitionId.split(":");
                            if (idSplit != null && idSplit.length == 3) {
                                if (idSplit[1].startsWith("8,")) {
//                                    usbPath = partition.path;
//                                    ilog("usbdisk===" + usbPath);
                                } else {
//                                    sdPath = partition.path;

//                                    ilog("sddisk===" + sdPath);
                                }
                            }
                        }
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            //write
            case R.id.msdToUsb_Button:
                readFlag = true;
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sta = System.currentTimeMillis();
                        while (readFlag) {
                            readFile();
                        }
                    }
                });

                mUsbToSd_Button.setEnabled(false);
                msdToUsb_Button.setEnabled(false);
                mStopRead.setEnabled(false);

                break;
            case R.id.mStopW_Button:
                readFlag = false;
                mUsbToSd_Button.setEnabled(true);
                msdToUsb_Button.setEnabled(true);
                mStopRead.setEnabled(true);


                break;
            //read
            case R.id.mUsbToSd_Button:
                wirteFlag = true;
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        stt = System.currentTimeMillis();
                        while (wirteFlag) {
                            writeFile();
                        }
                    }
                });
                mUsbToSd_Button.setEnabled(false);
                msdToUsb_Button.setEnabled(false);
                mStopWrite.setEnabled(false);

                break;
            case R.id.mStopR_Button:
                wirteFlag = false;
                mUsbToSd_Button.setEnabled(true);
                msdToUsb_Button.setEnabled(true);
                mStopWrite.setEnabled(true);
                break;

        }
    }

    public class DiskInfo {
        public String diskId;
        public String name;
        //public ArrayList diskPartitions = new ArrayList<>();
        public ArrayList<DiskPartition> diskPartitions = new ArrayList<DiskPartition>();
    }

    public class DiskPartition {
        public String name;
        public String partitionId;
        public String diskId;
        public long totalSize;
        public long avlableSize;
        public String path;
    }

    public static void getStorageBlockInfo(DiskPartition info) {
        if (TextUtils.isEmpty(info.path))
            return;
        android.os.StatFs statfs = new android.os.StatFs(info.path);
        long nBlocSize = statfs.getBlockSizeLong();
        long blockCountLong = statfs.getBlockCountLong();
        long nAvailaBlock = statfs.getAvailableBlocksLong();
        info.totalSize = blockCountLong * nBlocSize;
        info.avlableSize = nBlocSize * nAvailaBlock;
    }


    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private PendingIntent pi;
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_USB_PERMISSION://接受到自定义广播
                    setMsg("接收到自定义广播");
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {  //允许权限申请
                        if (usbDevice != null) {  //Do something
                            setMsg("用户已授权，可以进行读取操作" + usbDevice.getDeviceName());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        } else {
                            setMsg("未获取到设备信息");
                        }
                    } else {
                        setMsg("用户未授权，读取失败" + usbDevice.getDeviceName());

                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED://接收到存储设备插入广播
                    UsbDevice device_add = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device_add != null) {

                        setMsg("接收到存储设备插入广播，尝试读取");
                        readDeviceList();
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED://接收到存储设备拔出广播
                    UsbDevice device_remove = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device_remove != null) {

                        setMsg("接收到存储设备拔出广播");

                    }
                    break;
            }
        }

    };

    private void readDeviceList() {
        setMsg("开始读取设备列表...");
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //获取存储设备
        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        for (UsbMassStorageDevice device : storageDevices) {//可能有几个 一般只有一个 因为大部分手机只有1个otg插口
            if (usbManager.hasPermission(device.getUsbDevice())) {//有就直接读取设备是否有权限
                setMsg("检测到有权限，直接读取");
            } else {//没有就去发起意图申请
                setMsg("检测到设备，但是没有权限，进行申请");
                usbManager.requestPermission(device.getUsbDevice(), pendingIntent); //该代码执行后，系统弹出一个对话框，
            }
        }
        if (storageDevices.length == 0) setMsg("未检测到有任何存储设备插入");
    }

    private void registerReceiver() {
        //监听otg插入 拔出
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbDeviceStateFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        usbDeviceStateFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        registerReceiver(mUsbReceiver, usbDeviceStateFilter);
        //注册监听自定义广播
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

    }


    private void initview() {
        executorService = Executors.newCachedThreadPool();
        main_tv_msg = (TextView) findViewById(R.id.main_tv_msg);
        mUsbToSd_Button = (Button) findViewById(R.id.mUsbToSd_Button);
        msdToUsb_Button = (Button) findViewById(R.id.msdToUsb_Button);
        mStopRead = (Button) findViewById(R.id.mStopR_Button);
        mStopWrite = (Button) findViewById(R.id.mStopW_Button);
        mUsbToSd_Button.setOnClickListener(this);
        msdToUsb_Button.setOnClickListener(this);
        mStopRead.setOnClickListener(this);
        mStopWrite.setOnClickListener(this);
        main_sv = (ScrollView) findViewById(R.id.main_sv);

    }
   private void writeFile() {
       FileInputStream fis = null;
       FileOutputStream fout = null;
       //sd to usb 写入文件
       try {
           getFile();//读取usb路径
           final String filepath = usbpktmp + "/" + "logcat.txt";
           final File oldFile = new File(filepath);
           if (!isCreateFile) {
               this.runOnUiThread(new Runnable() {
                   @Override
                   public void run() {

                       setMsg("源文件不存在...准备创建..."+oldFile.getPath());
                   }
               });
               isCreateFile = CreateFile.createFile(filepath, fileSize, CreateFile.FileUnit.GB);
           } else {
               fis = new FileInputStream(oldFile);
               final File file = new File(sdpktmp + "/" + "logcat.txt");
               if (file.exists()) {
                   file.delete();
               }
               fout = new FileOutputStream(file);
               long start = 0; //开始时间
               long startSec = 0;//每秒开始时间
               long end = 0;//结束时间
               long oldLength = 0; //写入前文件大小
               long newLength = 0;//1秒秒后文件大小

               long secLength = 0;
               final String logpath = sdpktmp + "/" + "writelog.csv";

               FileChannel fcIn = fis.getChannel();
               FileChannel fcOs = fout.getChannel();
               ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
//               startSec = System.currentTimeMillis();//当前毫秒数
               oldLength = file.length();
               final String startformatdate = getCurFormatdate(start);
               while (true) {
                   buffer.clear();
                   int len = fcIn.read(buffer);
                   if (len == -1) {
                       break;
                   }
                   buffer.flip();
                   fcOs.write(buffer);
//                   end = System.currentTimeMillis();
                   //计算一秒的速率
//                   if (end - startSec > 1000) {
//                       startSec = end;
                       newLength = file.length();
                       secLength = ((newLength - oldLength) / 1024 / 1024);
//                       if (secLength > 1) {
                           oldLength = newLength;
                           averlength += secLength;
                           final long finalSecLength = secLength;
                           this.runOnUiThread(new Runnable() {
                               @Override
                               public void run() {
                                   String tmp = "速率：" + finalSecLength + "M/s" + "源文件：" + (oldFile.length() / 1024 / 1024) + "MB" + "目标文件：" + (file.length() / 1024 / 1024) + "MB";
                                   setMsg(tmp);
                               }
                           });
//                       }
//                   }
                   //30秒记录日志文件csv
               }

               final String endTime = getCurFormatdate(end);
               //30秒记录日志文件csv
               if (end - sta > 30000) {
                   String s = getCurFormatdate(sta);
                   String e = getCurFormatdate(end);
                   long avertmp = (averlength / 30 * 1000);
                   String str_log = "开始时间：" + s + " 平均" + avertmp + "M/s" + "结束时间" + e;
                   writeLogFile(logpath, str_log);
                   sta = end;
                   averlength = 0;
               }
               //校验文件
               checkFile(oldFile, file);
               this.runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       setMsg(endTime + "拷贝完成");
                   }
               });

           }
       } catch (Exception e) {
           e.printStackTrace();
       } finally {
           if (fout != null) {
               try {
                   fout.flush();
                   fout.close();
                   fis.close();
               } catch (IOException e) {
                   e.printStackTrace();
               }

           }
       }

   }

    private void readFile() {
        FileInputStream is = null;
        FileOutputStream fos = null;
        //sd to usb 写入文件
        try {
            getFile();//读取usb路径
            final String filepath = sdpktmp + "/" + "logcat.txt";
            final File oldFile = new File(filepath);
            if (!isCreateFile) {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMsg("源文件不存在...准备创建...");
                    }
                });
                isCreateFile = CreateFile.createFile(filepath, fileSize, CreateFile.FileUnit.GB);
            } else {
                is = new FileInputStream(oldFile);
                final File file = new File(usbpktmp + "/" + "logcat.txt");
                if (file.exists()) {
                    file.delete();
                }
                fos = new FileOutputStream(file);
                long start = 0; //开始时间
                long startSec = 0;//每秒开始时间
                long end = 0;//结束时间
                long oldLength = 0; //写入前文件大小
                long newLength = 0;//1秒秒后文件大小

                long secLength = 0;
                final String logpath = sdpktmp + "/" + "writelog.csv";

                FileChannel fcIn = is.getChannel();
                FileChannel fcOs = fos.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
//                start = startSec = System.currentTimeMillis();//当前毫秒数
                oldLength = file.length();
                final String startformatdate = getCurFormatdate(start);
                while (true) {
                    buffer.clear();
                    int len = fcIn.read(buffer);
                    if (len == -1) {
                        break;
                    }

                    buffer.flip();
                    fcOs.write(buffer);
                    end = System.currentTimeMillis();
                    //计算一秒的速率
//                    if (end - startSec > 1000) {
//                        startSec = end;
                        newLength = file.length();
                        secLength = ((newLength - oldLength) / 1024 / 1024);
//                        if (secLength > 1) {
                            oldLength = newLength;
                            averlength += secLength;
                            final long finalSecLength = secLength;
                            this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String tmp = "速率：" + finalSecLength + "M/s" + "源文件：" + (oldFile.length() / 1024 / 1024) + "MB" + "目标文件：" + (file.length() / 1024 / 1024) + "MB";
                                    setMsg(tmp);
                                }
                            });
//                        }
//                    }
                    //30秒记录日志文件csv
                }
                final String endTime = getCurFormatdate(end);
                //30秒记录日志文件csv
                if (end - sta > 30000) {
                    String s = getCurFormatdate(sta);
                    String e = getCurFormatdate(end);
                    long avertmp = (averlength / 30 * 1000);
                    String str_log = "开始时间：" + s + " 平均" + avertmp + "M/s" + "结束时间" + e;
                    writeLogFile(logpath, str_log);
                    sta = end;
                    averlength = 0;
                }


                //校验文件
                checkFile(oldFile, file);
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMsg(endTime + "拷贝完成");
                    }
                });

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }


    private void writeLogFile(String filename, String str) {
        File logFile = new File(filename);

        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true)));
            out.write(str + "\r\n");
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

/*
    private String readLogFile(String filename) {
        File file = new File(filename);
        try {
            FileInputStream fis = new FileInputStream(file);
            int length = fis.available();
            byte[] buffer = new byte[length];
            fis.read(buffer);
            res = new String(buffer, "UTF-8");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }
*/


    private void checkFile(final File sourceFile, final File targetFile) {

        if (sourceFile.length() / 1024 / 1024 == targetFile.length() / 1024 / 1024 && sourceFile.getName().equals(targetFile.getName())) {

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setMsg("文件校验成功");
                }
            });

        } else {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setMsg("文件校验失败");
                }
            });
        }
    }

    private String getCurFormatdate(long currentTime) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年-MM月dd日-HH时mm分ss秒");
        Date date = new Date(currentTime);
        return formatter.format(date);
    }


    //日志
    private void setMsg(String msg) {
        main_tv_msg.append(msg + "\n");
        main_sv.fullScroll(ScrollView.FOCUS_DOWN);//滚动到底部
    }


    private void bindView() {

        main_tv_msg.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                main_tv_msg.setText("");
                return true;
            }
        });
    }

    private void ilog(String str) {
        Log.e(TAG, str);
    }


//    private void testreadFile() {
//
//        getFile();
//
//        FileInputStream inputStream = null;
//        FileOutputStream fos = null;
//        try {
////            Log.d("TEST", "readFile");
//            String sdpath = sdpktmp + "/";
//            String usbpath = sdpktmp + "/";
//            String createFile = usbpktmp + "logcat.txt";
//            final File oldFile = new File(usbpath + "logcat.txt");
//            Log.e("TEST", oldFile.toString());
//            if (oldFile.exists()) {
////                Log.e("TEST", "oldFile");
//                inputStream = new FileInputStream(oldFile);
////                Log.e("TEST", "inputStream");
//                final File file = new File(sdpath + "123.txt");
//                Log.e("TEST", file.toString());
////                file.createNewFile();
//                fos = new FileOutputStream(file);
//
//                FileChannel fcnIn = inputStream.getChannel();
//                FileChannel fcnOut = fos.getChannel();
//                ByteBuffer buffer = ByteBuffer.allocate(1024);//1Mb
//
//                Log.e("TEST", "fos" + usbpath);
//                while (readFlag) {
//                    buffer.clear();
//                    int length = fcnIn.read(buffer);
//                    if (length == -1) {
//                        break;
//                    }
////                    fcnIn.transferTo(0,fcnOut.size(),fcnOut);
//
//                   this.runOnUiThread(new Runnable() {
//                       @Override
//                       public void run() {
//                           setMsg("source:"+ oldFile.getPath());
//                           setMsg("target:"+file.getPath());
//                           setMsg("target:"+file.length());
//                       }
//                   });
//                    buffer.flip();
//                    fcnOut.write(buffer);
//
//                    Log.e("TEST", "fos" + file.length());
//                }
//                Log.e("TEST", "文件拷贝完成" + file.length());
//
//            } else {
//                CreateFile.createFile(createFile, 1, CreateFile.FileUnit.GB);
//                Log.e("TEST", "文件不存在");
//
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (inputStream != null && fos != null) {
//                try {
//                    inputStream.close();
//                    fos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//            }
//        }
//
//    }


}



