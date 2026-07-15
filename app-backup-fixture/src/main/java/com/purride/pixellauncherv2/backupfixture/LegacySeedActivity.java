package com.purride.pixellauncherv2.backupfixture;

import android.app.Activity;
import android.os.Bundle;

/** 仅用于触发历史夹具 Application 初始化，不展示或接收用户数据。 */
public final class LegacySeedActivity extends Activity {

    /**
     * 创建后立即结束；测试数据已经由 Application 同步写入。
     *
     * @param savedInstanceState 平台传入的 Activity 恢复状态，本夹具不读取。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
