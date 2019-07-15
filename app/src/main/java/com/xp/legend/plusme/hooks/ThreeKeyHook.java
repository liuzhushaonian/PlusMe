package com.xp.legend.plusme.hooks;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ThreeKeyHook implements IXposedHookLoadPackage {


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals("com.android.systemui")){

            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBar",
                    lpparam.classLoader, "onThreeKeyChanged", int.class,new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);

                    int s= (int) param.args[0];

                    //1是最上面，2是中间，3是最下面

                    XposedBridge.log("status--->>"+s);

                }
            });

        }

    }
}
