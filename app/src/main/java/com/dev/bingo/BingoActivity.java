package com.dev.bingo;

import static com.dev.bingo.MainActivity.databaseUrl;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.firebase.ui.common.ChangeEventType;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BingoActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_CREATED = 1;
    public static final int STATUS_JOINED = 2;
    public static final int STATUS_CREATOR_TURN = 3;
    public static final int STATUS_JOINED_TURN = 4;
    public static final int STATUS_CREATOR_BINGO = 5;
    public static final int STATUS_JOINED_BINGO = 6;
    private static final String TAG = BingoActivity.class.getSimpleName();
    private String roomId;
    private TextView tvInfo;
    private boolean isCreator;
    private TextView info;
    private RecyclerView rvBingo;
    private FirebaseRecyclerAdapter<Boolean, NumberHolder> adapter;

    private boolean isMyTurn = false;
    private ValueEventListener statusListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            long status = (long) snapshot.getValue();
            switch ((int) status) {
                case STATUS_CREATED:
                    info.setText("等待其他玩家加入");
                    break;
                case STATUS_JOINED:
                    info.setText("玩家已加入");
                    FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                            .child(roomId)
                            .child("status")
                            .setValue(STATUS_CREATOR_TURN);
                    break;
                case STATUS_CREATOR_TURN:
                    setMyTurn(isCreator);
                    break;
                case STATUS_JOINED_TURN:
                    setMyTurn(!isCreator);
                    break;
                case STATUS_CREATOR_BINGO:
                    info.setText("");
                    new AlertDialog.Builder(BingoActivity.this)
                            .setTitle("BINGO")
                            .setMessage(isCreator ? "恭喜你，賓果了!" : "對方賓果了")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    endGame();
                                }
                            }).show();
                    break;
                case STATUS_JOINED_BINGO:
                    info.setText("");
                    new AlertDialog.Builder(BingoActivity.this)
                            .setTitle("BINGO")
                            .setMessage(!isCreator ? "恭喜你，賓果了!" : "對方賓果了")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    endGame();
                                }
                            }).show();

                    break;
            }

        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {

        }
    };

    private void endGame() {
        FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                .child(roomId)
                .child("status")
                .removeEventListener(statusListener);
        if (isCreator) {
            FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                    .child(roomId)
                    .removeValue();
        }
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bingo);
        findViews();
        roomId = getIntent().getStringExtra("ROOM_ID");
        isCreator = getIntent().getBooleanExtra("IS_CREATOR", false);
        Log.d(TAG, "onCreate: " + roomId + "/" + isCreator);
        if (isCreator) {
            // 在database裡準備25顆球
            for (int i = 0; i<25; i++) {
                FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                        .child(roomId)
                        .child("numbers")
                        .child(String.valueOf(i+1))
                        .setValue(false);
                FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                        .child(roomId)
                        .child("status")
                        .setValue(STATUS_CREATED);
            }
        } else {
            FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                    .child(roomId)
                    .child("status")
                    .setValue(STATUS_JOINED);
        }
        final Map<Integer, Integer> numberMap = new HashMap<>();
        List<NumberButton> buttons = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            NumberButton button = new NumberButton(this);
            button.setNumber(i+1);
            buttons.add(button);
        }
        // 打亂位置
        Collections.shuffle(buttons);
        for (int i = 0; i < 25; i++) {
            numberMap.put(buttons.get(i).getNumber(), i);
        }

        // adapter
        Query query = FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                .child(roomId)
                .child("numbers")
                .orderByKey();
        FirebaseRecyclerOptions<Boolean> options = new FirebaseRecyclerOptions.Builder<Boolean>()
                .setQuery(query, Boolean.class)
                        .build();
        adapter = new FirebaseRecyclerAdapter<Boolean, NumberHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull NumberHolder holder, int position, @NonNull Boolean model) {
                holder.button.setText(String.valueOf(buttons.get(position).getNumber()));
                holder.button.setNumber(buttons.get(position).getNumber());
                holder.button.setEnabled(!buttons.get(position).picked);
                holder.button.setOnClickListener(BingoActivity.this);
            }

            @Override
            public void onChildChanged(@NonNull ChangeEventType type, @NonNull DataSnapshot snapshot, int newIndex, int oldIndex) {
                super.onChildChanged(type, snapshot, newIndex, oldIndex);
                Log.d(TAG, "onChildChanged: " + type + "/" + snapshot.getKey() + "/" + snapshot.getValue());
                if (type == ChangeEventType.CHANGED) {
                    int number = Integer.parseInt(snapshot.getKey());
                    int position = numberMap.get(number);
                    boolean picked = (boolean) snapshot.getValue();
                    buttons.get(position).setPicked(picked);
                    NumberHolder holder = (NumberHolder) rvBingo.findViewHolderForAdapterPosition(position);
                    holder.button.setEnabled(!picked);
                    // 檢查有無Bingo
                    int[] nums = new int[25];
                    for (int i = 0; i < 25; i++) {
                        nums[i] = buttons.get(i).isPicked() ? 1: 0;
                    }
                    int bingo = 0;
                    for (int i = 0; i < 5; i++) {
                        int sum = 0;
                        for (int j = 0; j < 5; j++) {
                            sum += nums[i*5 + j];
                        }
                        bingo += (sum == 5) ? 1: 0;
                        sum = 0;
                        for (int j = 0; j < 5; j++) {
                            sum += nums[j*5 + i];
                        }
                        bingo += (sum == 5) ? 1: 0;
                    }
                    Log.d(TAG, "onChildChanged: bingo:" + bingo);
                    if (bingo > 0) {
                        FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                                .child(roomId)
                                .child("status")
                                .setValue(isCreator ? STATUS_CREATOR_BINGO : STATUS_JOINED_BINGO);
                    }
                }
            }

            @NonNull
            @Override
            public NumberHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(BingoActivity.this)
                        .inflate(R.layout.item_bingo_button, parent, false);
                return new NumberHolder(view);
            }
        };
        findViews();
    }

    @Override
    public void onClick(View view) {
        if (isMyTurn) {
            int number = ((NumberButton)view).getNumber();
            FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                    .child(roomId)
                    .child("numbers")
                    .child(String.valueOf(number))
                    .setValue(true);
            FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                    .child(roomId)
                    .child("status")
                    .setValue(isCreator ? STATUS_JOINED_TURN : STATUS_CREATOR_TURN);
        }

    }

    class NumberHolder extends RecyclerView.ViewHolder {
        NumberButton button;
        public NumberHolder(@NonNull View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.button);
        }
    }

    private void findViews() {
        info = findViewById(R.id.tvInfo);
        rvBingo = findViewById(R.id.rvBingo);
        rvBingo.setHasFixedSize(true);
        rvBingo.setLayoutManager(new GridLayoutManager(this, 5));
        rvBingo.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        adapter.startListening();
        FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                .child(roomId)
                .child("status")
                .addValueEventListener(statusListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        adapter.stopListening();
        FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                .child(roomId)
                .child("status")
                .removeEventListener(statusListener);
    }

    public boolean isMyTurn() {
        return isMyTurn;
    }

    public void setMyTurn(boolean myTurn) {
        isMyTurn = myTurn;
        info.setText(myTurn? "請選號" : "等待對方選號");
    }
}