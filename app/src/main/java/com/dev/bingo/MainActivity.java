package com.dev.bingo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements FirebaseAuth.AuthStateListener, View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 100;
    public static final String databaseUrl = "https://bingo-c9ac5-default-rtdb.asia-southeast1.firebasedatabase.app";
    private FirebaseAuth auth;
    private TextView tvNick;
    private ImageView imgUser;
    private Group group;
    private int[] avatars = {R.drawable.avatar01, R.drawable.avatar02, R.drawable.avatar03, R.drawable.avatar04, R.drawable.avatar05, R.drawable.avatar06, R.drawable.avatar07, R.drawable.avatar08};
    private Member member;
    private FirebaseRecyclerAdapter<Room, RoomHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUiComponent();
    }

    private void initUiComponent() {
        auth = FirebaseAuth.getInstance();
        tvNick = findViewById(R.id.tvNickname);
        imgUser = findViewById(R.id.imgUser);
        tvNick.setOnClickListener(view -> showNicknameDialog());
        group = findViewById(R.id.groupAvatars);
        group.setVisibility(View.GONE);
        imgUser.setOnClickListener(view -> {
            boolean visible = group.getVisibility() == View.GONE ? false : true;
            group.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.avatar1).setOnClickListener(this);
        findViewById(R.id.avatar2).setOnClickListener(this);
        findViewById(R.id.avatar3).setOnClickListener(this);
        findViewById(R.id.avatar4).setOnClickListener(this);
        findViewById(R.id.avatar5).setOnClickListener(this);
        findViewById(R.id.avatar6).setOnClickListener(this);
        findViewById(R.id.avatar7).setOnClickListener(this);
        findViewById(R.id.avatar8).setOnClickListener(this);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            EditText roomEdit = new EditText(this);
            roomEdit.setText("Welcome");
            new AlertDialog.Builder(this)
                    .setTitle("Room Name")
                    .setMessage("Please enter your room name")
                    .setView(roomEdit)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String roomName = roomEdit.getText().toString();
                            DatabaseReference rooms = FirebaseDatabase.getInstance(databaseUrl).getReference("rooms");
                            DatabaseReference roomRef = rooms.push();
                            String key = roomRef.getKey();
                            Room room = new Room(key, roomName, member);
                            roomRef.setValue(room, new DatabaseReference.CompletionListener() {
                                        @Override
                                        public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                            if (error == null) {
                                                Intent intent = new Intent(MainActivity.this, BingoActivity.class);
                                                intent.putExtra("ROOM_ID", room.getId());
                                                intent.putExtra("IS_CREATOR", true);
                                                startActivity(intent);
                                            }
                                        }
                                    });
                        }
                    }).setNeutralButton("Cancel", null)
                    .show();
        });
        RecyclerView rvRooms = findViewById(R.id.rvRooms);
        rvRooms.setHasFixedSize(true);
        rvRooms.setLayoutManager(new LinearLayoutManager(this));


        Query query = FirebaseDatabase.getInstance(databaseUrl).getReference("rooms")
                .limitToLast(30); // 最後30筆資料
        FirebaseRecyclerOptions<Room> option = new FirebaseRecyclerOptions.Builder<Room>()
                .setQuery(query, Room.class)
                .build();
        adapter = new FirebaseRecyclerAdapter<Room, RoomHolder>(option) {
            @Override
            protected void onBindViewHolder(@NonNull RoomHolder holder, int position, @NonNull Room model) {
                holder.imgHead.setImageResource(avatars[model.init.getAvatarId()]);
                holder.tvRoomName.setText(model.title);
                holder.itemView.setOnClickListener(view -> {
                    Log.d(TAG, "onClick: " + model.getId());
                    Intent intent = new Intent(MainActivity.this, BingoActivity.class);
                    intent.putExtra("ROOM_ID", model.getId());
                    startActivity(intent);
                });
            }

            @NonNull
            @Override
            public RoomHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_room, parent, false);
                return new RoomHolder(view);
            }
        };
        rvRooms.setAdapter(adapter); //adapter有生命週期 接下來去onStart設定
    }

    public class RoomHolder extends RecyclerView.ViewHolder {
        ImageView imgHead;
        TextView tvRoomName;

        public RoomHolder(@NonNull View itemView) {
            super(itemView);
            imgHead = itemView.findViewById(R.id.imgHead);
            tvRoomName = itemView.findViewById(R.id.tvRoomName);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(this);
        adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        auth.removeAuthStateListener(this);
        adapter.stopListening();
    }
    // 右上角menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    // 右上角menu

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_menu_signout:
                auth.signOut();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
        // 前往登入頁
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            // 使用者有登入
            Log.d(TAG, "onAuthStateChanged: " + user.getEmail() + "/" + user.getUid());
            DatabaseReference databaseReference = FirebaseDatabase.getInstance(databaseUrl)
                    .getReference("users");
            String displayName = user.getDisplayName();
            String uid = user.getUid();
            // 設定displayName
            databaseReference.child(uid)
                    .child("displayName")
                    .setValue(displayName)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "FirebaseDatabase Complete");
                        }
                    });
            // 設定uid
            databaseReference.child(uid)
                    .child("uid")
                    .setValue(uid)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "FirebaseDatabase Complete");
                        }
                    });

            // 拿取整筆會員資料物件
            databaseReference.child(user.getUid())
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            member = snapshot.getValue(Member.class);
                            if (member != null) {
                                if (member.getNickname() != null) {
                                    tvNick.setText(member.getNickname());
                                } else {
                                    showNicknameDialog();
                                }
                                imgUser.setImageResource(avatars[member.getAvatarId()]);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });


        } else {
            startActivityForResult(
                    // 使用Firebase UI 登入
                    AuthUI.getInstance().createSignInIntentBuilder()
                            .setAvailableProviders(Arrays.asList(
                                    new AuthUI.IdpConfig.EmailBuilder().build(), // 使用email登入
                                    new AuthUI.IdpConfig.GoogleBuilder().build() // 使用Google登入 可以一直往下加登入方式
                            ))
                            .setIsSmartLockEnabled(false) // SmartLock 幫使用者記住登入資料 但開發時先使用false 以利不同帳號測試 上線時再註解掉
                            .build()
                    , RC_SIGN_IN);
        }
    }

    private void showNicknameDialog() {
        EditText nickEdit = new EditText(this);
        nickEdit.setText(tvNick.getText().toString());

        new AlertDialog.Builder(this)
                .setTitle("Your nickname")
                .setMessage("Please enter your nickname")
                .setView(nickEdit)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String nickname = nickEdit.getText().toString();
                        FirebaseDatabase.getInstance(databaseUrl).getReference("users")
                                .child(auth.getUid())
                                .child("nickname")
                                .setValue(nickname);
                    }
                }).show();
    }

    @Override
    public void onClick(View view) {
        if (view instanceof ImageView) {
            int avatarId = 0;
            switch (view.getId()) {
                case R.id.avatar1:
                    avatarId = 0;
                    break;
                case R.id.avatar2:
                    avatarId = 1;
                    break;
                case R.id.avatar3:
                    avatarId = 2;
                    break;
                case R.id.avatar4:
                    avatarId = 3;
                    break;
                case R.id.avatar5:
                    avatarId = 4;
                    break;
                case R.id.avatar6:
                    avatarId = 5;
                    break;
                case R.id.avatar7:
                    avatarId = 6;
                    break;
                case R.id.avatar8:
                    avatarId = 7;
                    break;
            }
            group.setVisibility(View.GONE);
            FirebaseDatabase.getInstance(databaseUrl).getReference("users")
                    .child(auth.getUid())
                    .child("avatarId")
                    .setValue(avatarId);
        }
    }


}