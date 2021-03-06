package com.libo.testwechat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.libo.testwechat.http.Apis;
import com.libo.testwechat.http.MyCallback;
import com.libo.testwechat.util.AudioPlayer;
import com.libo.testwechat.util.PreferenceUtil;
import com.libo.testwechat.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

public class HomeActivity extends Activity implements Switch.OnCheckedChangeListener {
    private TextView mMessages, mWelcome;
    private Switch mSwitch;
    private Vibrator mVibrator;
    private MessageReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        //应用运行时，保持屏幕高亮，不锁屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mVibrator = (Vibrator) getApplicationContext().getSystemService(Service.VIBRATOR_SERVICE);
        mMessages = (TextView) findViewById(R.id.message);
        mWelcome = (TextView) findViewById(R.id.welcome);
        mSwitch = (Switch) findViewById(R.id.switchON_off);
        mSwitch.setOnCheckedChangeListener(this);
        mSwitch.setChecked(PreferenceUtil.getInstance().getString(Constant.STATUS, "").equals("1"));

        String uname = PreferenceUtil.getInstance().getString(Constant.USERNAME, "");
        SpannableStringBuilder builder = new SpannableStringBuilder("欢迎" + uname + "使用！");
        ForegroundColorSpan redSpan = new ForegroundColorSpan(Color.RED);
        builder.setSpan(redSpan, 2, 2 + uname.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mWelcome.setText(builder);
    }

    @Override
    protected void onResume() {
        IntentFilter filter = new IntentFilter("new_message");
        if (mReceiver == null)
            mReceiver = new MessageReceiver();
        //注册BroadcastReceiver
        registerReceiver(mReceiver, filter);
        super.onResume();

        App.getInstance().setLogin(true);

        findViewById(R.id.layout_error).setVisibility(Utils.isAccessibilitySettingsOn(this) ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.balance)).setText(PreferenceUtil.getInstance().getString(Constant.BALANCE, "0"));
        if (PreferenceUtil.getInstance().getBoolean(Constant.NEED_SOUND, false)) {
            PreferenceUtil.getInstance().put(Constant.NEED_SOUND, false);
            int code = PreferenceUtil.getInstance().getInt(Constant.CURRENT, 0);
            if (code == 666) {
                mVibrator.vibrate(new long[]{100, 5000}, 0);
                showAlertDialog(PreferenceUtil.getInstance().getString(Constant.CURRENT_TIP, ""));
            }
        }
    }

    class PlayThread extends Thread {
        private volatile boolean flag = true;

        public void stopTask() {
            flag = false;
        }

        @Override
        public void run() {
            super.run();
            while (true) {
                if (flag) {
                    try {
                        AudioPlayer.getInstance().playMusic();
                        Thread.sleep(1200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private PlayThread mPlayThread;

    public void showAlertDialog(String message) {
        try {
            if (mPlayThread == null)
                mPlayThread = new PlayThread();
            if (!mPlayThread.isAlive())
                mPlayThread.start();

            AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
            builder.setTitle("提示");
            builder.setMessage(message);
            builder.setCancelable(false);
            builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    mPlayThread.stopTask();
                    if (mVibrator != null)
                        mVibrator.cancel();
                }

            });
            builder.create().show();
        } catch (Exception e) {

        }
    }

    public void open(View view) {
        Utils.openAccessibilitySettings(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!buttonView.isPressed()) return;
        if (isChecked)
            setOn();
        else
            setOff();
    }

    public void setOn() {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) return;
        Apis.getInstance().setOn(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                mVibrator.vibrate(1500);
                PreferenceUtil.getInstance().put(Constant.STATUS, "1");
                Toast.makeText(App.getInstance(), "开启成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                mVibrator.vibrate(1500);
                Toast.makeText(App.getInstance(), "异常", Toast.LENGTH_SHORT).show();
                //由关到开  失败则还是关
                mSwitch.setChecked(false);
            }
        });
    }

    public void setOff() {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) return;
        Apis.getInstance().setOff(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                mVibrator.vibrate(1500);
                PreferenceUtil.getInstance().put(Constant.STATUS, "2");
                Toast.makeText(App.getInstance(), "关闭成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                mVibrator.vibrate(1500);
                Toast.makeText(App.getInstance(), "异常", Toast.LENGTH_SHORT).show();
                //由开到关  失败则还是开
                mSwitch.setChecked(true);
            }
        });
    }

    public void logout(View view) {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        Apis.getInstance().setOff(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                mSwitch.setChecked(false);
                App.getInstance().setLogin(false);
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                HomeActivity.this.startActivity(intent);
                finish();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                Toast.makeText(App.getInstance(), "异常", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void refresh(View view) {
        findViewById(R.id.layout_error).setVisibility(Utils.isAccessibilitySettingsOn(this) ? View.GONE : View.VISIBLE);

        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        Apis.getInstance().getUserInfo(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                try {
                    JSONObject jsonObject = new JSONObject(body);
                    PreferenceUtil.getInstance().put(Constant.BILL_NAME, jsonObject.optString("billname"));
                    PreferenceUtil.getInstance().put(Constant.STATUS, jsonObject.optString("status"));
                    PreferenceUtil.getInstance().put(Constant.BALANCE, jsonObject.optString("balance"));
                    PreferenceUtil.getInstance().put(Constant.MESSAGE, jsonObject.optString("message"));
                    mSwitch.setChecked("1".equals(jsonObject.optString("status")));
                    ((TextView) findViewById(R.id.balance)).setText(jsonObject.optString("balance"));
                    PreferenceUtil.getInstance().put(Constant.USERNAME, jsonObject.optString("user_name"));
                    Toast.makeText(HomeActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                Toast.makeText(App.getInstance(), "异常", Toast.LENGTH_SHORT).show();
            }
        });
    }


    class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String action = arg1.getAction();
            if (action.equals("new_message")) {
                String msg = arg1.getStringExtra("msg");
                mMessages.setText(msg);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //注销BroadcastReceiver
        if (null != mReceiver)
            unregisterReceiver(mReceiver);
        super.onPause();
    }

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
        builder.setMessage("确认退出吗？");
        builder.setTitle("提示");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }

        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });
        builder.create().show();
    }

    public void billList(View view) {
        Intent intent = new Intent(this, BillListActivity.class);
        startActivity(intent);
    }

    public void edit(View view) {
        final EditText editText = new EditText(this);
        editText.setHeight(180);
        editText.setText(PreferenceUtil.getInstance().getString(Constant.MESSAGE, ""));
        new AlertDialog.Builder(this)
                .setTitle("修改下注信息")
                .setView(editText)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TextUtils.isEmpty(editText.getText().toString().trim())) {
                            Toast.makeText(HomeActivity.this, "下注信息不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
                        Apis.getInstance().editMessage(uid, editText.getText().toString(), new MyCallback() {
                            @Override
                            public void responeData(String body, JSONObject json) {
                                Toast.makeText(HomeActivity.this, json.optString("message"), Toast.LENGTH_SHORT).show();
                                PreferenceUtil.getInstance().put(Constant.MESSAGE, editText.getText().toString());
                            }

                            @Override
                            public void responeDataFail(int responseStatus, String errMsg) {
                                Toast.makeText(HomeActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .show();
    }

    public void sendChat(View view) {
        final EditText editText = new EditText(this);
        editText.setHeight(180);
        editText.setHint("请输入内容");
        new AlertDialog.Builder(this)
                .setTitle("发送消息")
                .setView(editText)
                .setNegativeButton("取消", null)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TextUtils.isEmpty(editText.getText().toString().trim())) {
                            Toast.makeText(HomeActivity.this, "发送内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        PreferenceUtil.getInstance().put(Constant.MESSAGE_FOR_WECHAT, editText.getText().toString());
                    }
                })
                .show();
    }

    public void history(View view) {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }

    /**
     * 屏蔽返回键
     **/
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
