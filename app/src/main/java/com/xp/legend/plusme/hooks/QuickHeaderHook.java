package com.xp.legend.plusme.hooks;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.xp.legend.plusme.utils.ReflectUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QuickHeaderHook implements IXposedHookLoadPackage {

    private Activity activity;

    private int width,height;

    private ViewGroup header;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals("net.oneplus.launcher")){
            return;
        }

        XposedHelpers.findAndHookMethod("net.oneplus.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                activity= (Activity) param.thisObject;//赋值，获取Activity对象，方便使用上帝对象context以及一些只有Activity才能使用的方法
            }
        });

        XposedHelpers.findAndHookMethod("net.oneplus.launcher.quickpage.view.WelcomePanel",
                lpparam.classLoader, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);

                        TextView title= (TextView) XposedHelpers.getObjectField(param.thisObject,"mWelcomeTitle");
//
//                        String t=title.getText().toString();
//
//                        XposedBridge.log("title---->>"+t);

                        header= (ViewGroup) param.thisObject;//获取头部

                        header.setBackgroundTintList(null);//清除原有背景



//                        header.setBackgroundColor(Color.GREEN);

//                        XposedBridge.log("me--->>change"+header.toString());

                        //仅仅针对title的点击来打开相册并设置
                        title.setOnClickListener(v -> {

                            width= (header.getWidth());

                            height= (header.getHeight());

//                            XposedBridge.log("click!!--->>"+width+"\nheight--->>"+height);
                            openAlbum(54321);

                        });

                        title.setOnLongClickListener(v -> {

                            if (header!=null){

                                cleanAndResumeDefaultBg();

                                deleteLocalBitmap();
                            }

                            return true;
                        });


                        setBg(getLocalBitmap());//初始化



                    }
                });


        /**
         * 获得选取的图片并剪切
         * hook Activity的onActivityResult方法，同时监听本身需要监听的两个requestCode，如果不是，则返回原本的处理方式
         */
        XposedHelpers.findAndHookMethod("net.oneplus.launcher.Launcher",
                lpparam.classLoader, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {

                        int requestCode= (int) param.args[0];

                        if (requestCode==54321){//剪切


                            Intent intent= (Intent) param.args[2];

                            if (intent==null||intent.getData()==null){
                                return null;
                            }

                            Uri uri=getFileUri(saveAsFile(intent.getData()));

                            if (uri!=null){

                                startCropImage(uri,width,height,12345);


                            }



                            return null;
                        }

                        if (requestCode==12345){//获取剪切好以后的图片并设置上

                            Intent data= (Intent) param.args[2];

                            if (data==null||data.getData()==null){
                                return null;
                            }

                            saveBitmapAndSetBg(data.getData());

                            return null;
                        }

                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                });


    }


    /**
     * 打开相册
     * @param code 返回码
     */
    private void openAlbum(int code) {

        if (activity==null){
            return;
        }

        Intent intent=new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        activity.startActivityForResult(intent,code);
    }



    /**
     * 剪切图片
     * @param uri 文件uri
     * @param w 宽度
     * @param h 高度
     * @param code 返回码
     */
    private void startCropImage(Uri uri, int w, int h, int code) {

        if (activity==null){

            XposedBridge.log("activity is null");

            return;
        }

        Intent intent = new Intent("com.android.camera.action.CROP");
        //设置数据uri和类型为图片类型
        intent.setDataAndType(uri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        //显示View为可裁剪的
        intent.putExtra("crop", true);
        //裁剪的宽高的比例为1:1
        intent.putExtra("aspectX", w);
        intent.putExtra("aspectY", h);
        //输出图片的宽高均为150
        intent.putExtra("outputX", w);
        intent.putExtra("outputY", h);

        //裁剪之后的数据是通过Intent返回
        intent.putExtra("return-data", false);

//        intent.putExtra("outImage", Bitmap.CompressFormat.JPEG.toString());
        intent.putExtra("noFaceDetection",true);
//        intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
        activity.startActivityForResult(intent, code);
    }


    /**
     * 保存为文件，本身已经有权限，但是第一次需要手动改变一次权限，不然将无法使用
     */
    private File saveAsFile(Uri uri) throws Exception {

        File outFile = new File(Environment.getExternalStorageDirectory()+"/plusme","pic");//临时文件

        if (!outFile.getParentFile().exists()){
            if (outFile.getParentFile().mkdirs()){

                XposedBridge.log("get!");

            }else {
                XposedBridge.log("can not get the permission!");

                Toast.makeText(activity, "无法获取sd卡权限，请在设置-应用和通知-查看全部应用（显示系统进程）-一加桌面-权限-存储重新打开一次再尝试", Toast.LENGTH_LONG).show();
                return null;
            }
        }

        InputStream in = null;
        FileOutputStream out = null;
//        BitmapFactory.Options options = null;
        try {

            // save bitmap
            in = AndroidAppHelper.currentApplication().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, null);
            out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out);
            out.flush();
            bitmap.recycle();
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            try { out.close(); } catch (Exception ignored) { }
        }
        return outFile;


    }

    /**
     * 获取某个文件的Uri，主要解决Google相册无法剪切图片的问题
     * @param file 传入文件
     * @return 返回该文件的Uri
     */
    private Uri getFileUri(File file){

        if (file==null){

            Log.d("file-->>","file is null");

            return null;
        }

        try {
            return Uri.parse(MediaStore.Images.Media.insertImage(
                    AndroidAppHelper.currentApplication().getContentResolver(), file.getAbsolutePath(), null, null));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;

    }

    //同时设置以及保存，用于手动设置
    private void saveBitmapAndSetBg(Uri uri){

        Bitmap bitmap=null;

        try {
            bitmap=BitmapFactory.decodeStream(AndroidAppHelper.currentApplication().getContentResolver().openInputStream(uri));

            saveBg(bitmap);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        setBg(bitmap);

        Toast.makeText(activity, "设置成功", Toast.LENGTH_SHORT).show();


    }

    /**
     * 设置背景
     * @param bg bitmap
     */
    private void setBg(Bitmap bg){

        if (bg==null){

            XposedBridge.log("bg is null");
            return;
        }

        //将bitmap转为drawble
        BitmapDrawable drawable=new BitmapDrawable(AndroidAppHelper.currentApplication().getResources(),bg);

        header.setBackground(drawable);

    }

    /**
     * 将设置好的背景保存到本地，提供重启后自动查询并设置
     * @param bitmap 返回bitmap
     */
    private void saveBg(Bitmap bitmap){

        File file=new File(AndroidAppHelper.currentApplication().getFilesDir(),"bg");

        try {
            FileOutputStream outputStream=new FileOutputStream(file);

            bitmap.compress(Bitmap.CompressFormat.WEBP,100,outputStream);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * 一开始初始化完成自动寻找背景
     * @return 返回bitmap
     */
    private Bitmap getLocalBitmap(){

        File file=new File(AndroidAppHelper.currentApplication().getFilesDir(),"bg");

        if (file.exists()){

            return BitmapFactory.decodeFile(file.getAbsolutePath());

        }

        return null;

    }

    /**
     * 清除已设置背景，恢复默认
     */
    private void cleanAndResumeDefaultBg(){

        int id=ReflectUtil.getColorId(AndroidAppHelper.currentApplication(),"quick_page_item_background_color");

        int color=AndroidAppHelper.currentApplication().getResources().getColor(id);

        header.setBackground(null);

        header.setBackgroundColor(color);


    }

    /**
     * 删除本地文件
     */
    private void deleteLocalBitmap(){

        File file=new File(AndroidAppHelper.currentApplication().getFilesDir(),"bg");

        if (file.exists()){

            if (file.delete()){

                Toast.makeText(activity, "清除成功", Toast.LENGTH_SHORT).show();
            }

        }

    }

}
