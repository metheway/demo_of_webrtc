package com.baidu.demo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


public class MainMenuAct extends AppCompatActivity implements View.OnClickListener{

    private Button oneToOneAVButton;
    private Button oneToOneDCButton;
    private Button oneToManyButton;
    private Button multiToMultiMeshButton;
    private EditText roomIdEditText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        initView();
    }

    private void initView() {
        oneToOneAVButton = findViewById(R.id.one_to_one_av_stream_bt);
        oneToOneDCButton = findViewById(R.id.dc_stream_bt);
        oneToManyButton = findViewById(R.id.one_to_many_av_stream_bt);
        multiToMultiMeshButton = findViewById(R.id.multi_to_multi_av_stream_bt);
        roomIdEditText = findViewById(R.id.room_id_et);
        oneToOneAVButton.setOnClickListener(this);
        oneToOneDCButton.setOnClickListener(this);
        oneToManyButton.setOnClickListener(this);
        multiToMultiMeshButton.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        Intent intent = null;
        switch (view.getId()) {
            case R.id.one_to_many_av_stream_bt:
                intent = new Intent(MainMenuAct.this, OneToMultiAVAct.class);
                break;
            case R.id.one_to_one_av_stream_bt:
                intent = new Intent(MainMenuAct.this, OneToOneAVAct.class);
                break;
            case R.id.dc_stream_bt:
                break;
            case R.id.multi_to_multi_av_stream_bt:
                intent = new Intent(MainMenuAct.this, MultiToMultiAct_mesh.class);
                break;
                default:break;
        }
        if (null != intent) {
            intent.putExtra("roomId", roomIdEditText.getText().toString());
            startActivity(intent);
        }
    }
}
