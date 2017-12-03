package exception.hejin.com;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 作者 : 贺金龙
 * 创建时间 :  2017/12/3 21:34
 * 类描述 : 用于处理全局异常的工具类,
 * 类说明 : 因为是全局变量,所以这里用一个单例
 * 由于全局异常应用的是Thread.UncaughtExceptionHandler接口
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    /*当你自己不进行处理的时候,要应用到系统的默认处理,所以这里要有默认的处理机制*/
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private Context mContext;
    /*用于存储日期的内容*/
    private Map<String, String> mExceptionInfo = new HashMap<>();
    /*用于存储设备信息于异常信息*/
    private DateFormat mFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA);

    /*1.单例模式*/
    private static CrashHandler mCrashHandler;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        if (mCrashHandler == null) {
            synchronized (CrashHandler.class) {
                if (mCrashHandler == null) {
                    mCrashHandler = new CrashHandler();
                }
            }
        }
        return mCrashHandler;
    }

    /**
     * author :  贺金龙
     * create time : 2017/12/3 22:12
     * description : 初始化
     */
    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        /*这里是设置自己的处理异常的方法,来处理异常*/
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * author :  贺金龙
     * create time : 2017/12/3 21:44
     * description :
     * instructions : 这个类中需要做的内容有
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        /*是否已经处理这个异常了*/
        if (isHandleException(e)) {/*没有人为处理,调用系统的处理*/
            if (mDefaultHandler != null) {
                /*调用系统的处理,就是弹出崩溃的对话框*/
                mDefaultHandler.uncaughtException(t, e);
            }
        } else {/*已经人为处理,这里给用户一个提示就可以了*/
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            /*结束当前进程*/
            Process.killProcess(Process.myPid());
            System.exit(1);


        }
    }

    /**
     * author :  贺金龙
     * create time : 2017/12/3 22:14
     * description : 是否处理已经处理异常了
     * instructions : 未处理调用系统默认的处理器处理
     * 1.收集错误信息
     * 2.保存错误信息
     * 3.上传到本地的服务器
     *
     * @param e 相应的异常
     * @return false 代表没有处理异常,true 代表已经处理异常了
     */
    private boolean isHandleException(Throwable e) {
        if (e == null) {/*当异常未空的时候,证明你没有处理,其实这里返回true也是可以的,但是为了更好的逻辑,所以这里返回false*/
            return false;
        }

        /*因为这个一定是在主线程执行的,所以这里要是谈Toast的话要创建线程进行弹出
        * 这里弹出的内容,找产品去定
        */
        new Thread() {
            @Override
            public void run() {
                super.run();
                Looper.prepare();
                Toast.makeText(mContext, "程序出现异常即将退出", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        };

        collectException();

        saveExceptionInfo(e);

        return true;
    }


    /**
     * author :  贺金龙
     * create time : 2017/12/3 22:32
     * description : 收集错误信息
     * instructions : 错误信息-1.型号2.品牌3.版本4.当前应用版本
     */
    private void collectException() {
        /*1.首先包名应该是我这个应用的错误信息*/
        PackageManager packageManager = mContext.getPackageManager();
        try {/*这里可以获取很多信息*/
            PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
            String packageName = TextUtils.isEmpty(packageInfo.packageName) ? "版本名称未设置" : packageInfo.packageName;
            String versionName = TextUtils.isEmpty(packageInfo.versionName) ? "版本号未设置" : packageInfo.versionName;
            String versionCode = TextUtils.isEmpty(String.valueOf(packageInfo.versionCode)) ? "版本号未设置" : String.valueOf(packageInfo.versionCode);

            mExceptionInfo.put("packageName", packageName);
            mExceptionInfo.put("versionName", versionName);
            mExceptionInfo.put("versionCode", versionCode);

            Field[] fields = Build.class.getFields();
            if (fields != null && fields.length > 0) {
                for (Field field : fields) {
                    field.setAccessible(true);
                    try {
                        mExceptionInfo.put(field.getName(), field.get(null).toString());
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * author :  贺金龙
     * create time : 2017/12/3 22:33
     * description : 保存错误信息
     * instructions : 以文件的形式保存错误信息
     *
     * @param e 错误信息
     */
    private void saveExceptionInfo(Throwable e) {
        /*获取相应的键值对*/
        StringBuffer stringBuffer = new StringBuffer();
        for (Map.Entry<String, String> entry :
                mExceptionInfo.entrySet()) {
            String keyName = entry.getKey();
            String keyValue = entry.getValue();

            stringBuffer.append(keyName + "=" + keyValue + "/n");
        }
        /*进行写入操作*/
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e.printStackTrace(printWriter);
        /*错误发生的原因*/
        Throwable cause = e.getCause();/*错误发生的原因*/

        /*当cause(错误原因不未空的时候,就要一直的去写,当为空的时候就停止写入了)*/
        while (cause != null) {
            /*进行写入*/
            cause.printStackTrace(printWriter);
            cause = e.getCause();
        }

        printWriter.close();//关闭流

        /*写入结果*/
        String result = writer.toString();
        stringBuffer.append(result);

        /*设置文件夹名称*/
        long currentTime = System.currentTimeMillis();
        String fileName = "crash-" + currentTime + ".log";

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_BAD_REMOVAL)) {
            String path = "/sdcard/carsh/";
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }


            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(path + fileName);
                fileOutputStream.write(stringBuffer.toString().getBytes());
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                try {
                    fileOutputStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }

    }
}
