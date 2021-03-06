package com.masonliu.decompileapk;


import com.masonliu.decompileapk.util.FileUtil;
import com.masonliu.decompileapk.util.MyRuntime;
import com.masonliu.decompileapk.util.StringUtil;
import com.masonliu.decompileapk.util.ZipUtil;

import java.io.File;

/**
 * 混淆：
 * !shrink
 * !warning
 * !optimize
 * !com.mason.**
 * keep attribute annotation
 *
 * @author liumeng
 */
public class Main {

    public static void main(String[] args) {
        String runtimeDir = System.getProperty("user.dir");
        System.out.println("user.dir（当前命令行所在目录）: " + runtimeDir);
        String jarParentPath = "";
        if (runInJar()) {
            jarParentPath = jarParentPath();
        } else {
            jarParentPath = runtimeDir;
        }
        System.out.println("user.dir（当前class或jar所在目录）: " + jarParentPath);

        String apkPath = runtimeDir + "/doc/test.apk";
        String outPath = runtimeDir + "/build/output";
        boolean isLog = true;
        boolean isBuild = false;
        boolean isCount = false;

        if (runInJar()) {
            MyJCommander jct = new MyJCommander(args);
            apkPath = jct.getApkPath();
            outPath = jct.getOutPath();
            isLog = jct.debug;
            isBuild = jct.build;
            isCount = jct.count;
        }

        MyRuntime myRuntime = MyRuntime.getMyRuntime();
        myRuntime.changeDir(jarParentPath + "/runLibs/");
        myRuntime.isLog = isLog;
        System.out.println("********************************************************");
        System.out.println("Processing...");

        if (isBuild) {
            recreateApk(myRuntime, outPath);
        } else if (isCount) {
            countMethodApk(myRuntime, apkPath);
        } else {
            decompileApk(myRuntime, apkPath, outPath);
        }

        System.out.println("Done!");
        System.out.println("********************************************************");
    }

    public static void recreateApk(MyRuntime myRuntime, String apktoolOutput) {
        System.out.println("Recreate apk...");
        //重新打包
        myRuntime.exec("java -jar apktool_2.0.0.3.jar b -f " + apktoolOutput);
        System.out.println("Find apk in " + apktoolOutput + "/dist/");
        System.out.println("The apk has no sign and no zipalign,please run 'jarsigner' and 'zipalign'.");
    }

    public static void countMethodApk(MyRuntime myRuntime, String apktoolOutput) {
        myRuntime.isLog = true;
        myRuntime.exec("java -jar dex-method-counts.jar " + apktoolOutput);
        myRuntime.isLog = false;
    }

    public static void decompileApk(MyRuntime myRuntime, String apkPath, String outPath) {
        outPath = outPath + "/decompile_" + System.currentTimeMillis();
        try {
            FileUtil.copy(apkPath, outPath + "/tem.apk");
            //使用apktool反编译可以得到smali和反编译后的xml
            myRuntime.exec("java -jar apktool_2.3.4.jar d " + outPath + "/tem.apk" + " -f -o " + outPath + "/apktool_output");
            //apk直接转成jar
            if (MyRuntime.isWindowsOS()) {
                myRuntime.exec(myRuntime.pb.directory().getAbsolutePath() + "/dex-tools-2.1-SNAPSHOT/d2j-dex2jar.bat -f -o " + outPath + "/classes_dex2jar.jar " + outPath + "/tem.apk");
            } else {
                myRuntime.exec(myRuntime.pb.directory().getAbsolutePath() + "/dex-tools-2.1-SNAPSHOT/d2j-dex2jar.sh -f -o " + outPath + "/classes_dex2jar.jar " + outPath + "/tem.apk");
            }
            //检测签名
            String signerLog = myRuntime.exec("java -jar apksigner_xyc000.jar verify -v --print-certs " + outPath + "/tem.apk");
            StringUtil.string2File(signerLog, outPath + "/signlog.txt");
            //unzip、反编译AndroidManifest（apktool反编译的不靠谱） 把 dex 和 AndroidManifest.xml copy到外边
            ZipUtil.UnZip(new File(outPath + "/tem.apk"), outPath + "/apkunzip");
            String trueAndroidManifest = myRuntime.exec("java -jar axmlprinter-0.1.7.jar " + outPath + "/apkunzip/AndroidManifest.xml");
            StringUtil.string2File(trueAndroidManifest, outPath + "/AndroidManifest.xml");
            FileUtil.copy(outPath + "/apkunzip/classes.dex", outPath + "/apktool_output/classes.dex");
            FileUtil.delete(outPath + "/apkunzip");
            //查看结果
            Result.handleResult(new String[]{outPath + "/AndroidManifest.xml", outPath + "/signlog.txt"});
            //删除签名信息临时文件
            FileUtil.delete(outPath + "/signlog.txt");
            //调用jd-ui
            if (MyRuntime.isWindowsOS()) {
                myRuntime.exec(myRuntime.pb.directory().getAbsolutePath() + "/jd-ui.exe " + outPath + "/classes_dex2jar.jar");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean runInJar() {
        return !System.getProperty("java.class.path").contains(";");
    }

    private static String jarParentPath() {
        return new File(System.getProperty("java.class.path")).getParentFile().getAbsolutePath();
    }
}
