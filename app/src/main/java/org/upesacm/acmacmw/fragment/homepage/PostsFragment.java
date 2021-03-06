package org.upesacm.acmacmw.fragment.homepage;

import android.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.upesacm.acmacmw.Manifest;
import org.upesacm.acmacmw.R;
import org.upesacm.acmacmw.activity.HomeActivity;
import org.upesacm.acmacmw.adapter.PostsRecyclerViewAdapter;
import org.upesacm.acmacmw.fragment.ImageUploadFragment;
import org.upesacm.acmacmw.listener.HomeActivityStateChangeListener;
import org.upesacm.acmacmw.listener.OnLoadMoreListener;
import org.upesacm.acmacmw.model.Member;
import org.upesacm.acmacmw.model.Post;
import org.upesacm.acmacmw.retrofit.HomePageClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;


public class PostsFragment extends Fragment
        implements OnLoadMoreListener,
        Callback<HashMap<String,Post>>,
        ValueEventListener,
        HomeActivityStateChangeListener{

    static final int CHOOSE_FROM_GALLERY=2;
    static final int REQUEST_IMAGE_CAPTURE = 1;

    static final int CAMERA_PERMISSION_REQUEST_CODE = 3;

    FragmentManager childFm;
    HomePageClient homePageClient;
    RecyclerView recyclerView;
    ProgressBar progressBar;
    private int monthCount=-1;
    private DatabaseReference postsReference;
    PostsRecyclerViewAdapter recyclerViewAdapter;

    Member signedInMember;

    public PostsFragment() {
        // Required empty public constructor
    }

    public static PostsFragment newInstance(HomePageClient homePageClient) {
        PostsFragment fragment = new PostsFragment();
        fragment.homePageClient = homePageClient;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("onCreate home page fragment");
        childFm=getChildFragmentManager();

        Bundle bundle=getArguments();
        if(bundle!=null) {

        }

        Calendar calendar = Calendar.getInstance();
        postsReference= FirebaseDatabase.getInstance()
                .getReference("posts/"+"Y"+calendar.get(Calendar.YEAR)+"/"
                        +"M"+calendar.get(Calendar.MONTH));
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        recyclerView=view.findViewById(R.id.posts_recyclerView);
        progressBar = view.findViewById(R.id.progress_bar_home);
        FloatingActionButton floatingActionButton = view.findViewById(R.id.cameraButton);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onCameraButtonClick(view);
            }
        });

        recyclerViewAdapter=new PostsRecyclerViewAdapter(recyclerView,homePageClient,signedInMember);
        recyclerViewAdapter.setOnLoadMoreListener(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(recyclerViewAdapter);

        /* ***************************Adding ValueEvent Listener******************************/
        postsReference.addValueEventListener(this);
        /* **********************************************************************************/

        progressBar.setVisibility(View.VISIBLE);

        ((HomeActivity)getActivity()).addOnHomeActivityStateChangeListener(this);
        return view;
    }




    /* ************************** functions for taking picture ***********************************/
    private void dispatchTakePictureIntent() {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
    }

    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        if(grantResults!=null) {
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED && grantResults[1]==PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            }
            else {
                Toast.makeText(getContext(),"Please grant camera permission",Toast.LENGTH_LONG).show();
                Log.i("MainActivity", "onRequestPermissionsResult Permission denied\n");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        System.out.println("onActivityResult Called");
        Bitmap imageBitmap = null;
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            System.out.println("request image capture");
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            System.out.println("byte array : "+byteArray);
            for(int i=0;i<byteArray.length;i++) {
                System.out.print(byteArray[i]);
            }
            Bundle args=new Bundle();
            args.putByteArray("image_data",byteArray);
            this.onNewPostDataAvailable(args);

        } else if (requestCode == CHOOSE_FROM_GALLERY && resultCode == RESULT_OK && resultCode!=RESULT_CANCELED) {
            System.out.println("choose from gallery");
            Uri uri = data.getData();
            try {
                imageBitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), uri);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byte[] byteArray = stream.toByteArray();
                Bundle args=new Bundle();
                args.putByteArray("image_data",byteArray);
                this.onNewPostDataAvailable(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onCameraButtonClick(View view) {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Add Photo!");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals("Take Photo")) {
                    if(ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED) {
                        dispatchTakePictureIntent();
                    }
                    else {
                        System.out.println("Permission for camera not granted. Requesting Permission");
                        ActivityCompat.requestPermissions(getActivity()
                                ,new String[]{android.Manifest.permission.CAMERA},PostsFragment.CAMERA_PERMISSION_REQUEST_CODE);
                    }

                } else if (options[item].equals("Choose from Gallery")) {
                    Intent intent = new Intent();
                    intent.setType("image/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Select Photo"), CHOOSE_FROM_GALLERY);
                } else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }
    /* ********************************************************************************************/




    /* @@@@@@@@@@@@@@@@@@@@@@@@@@@@ Overriden methods of ValueEventListener @@@@@@@@@@@@@@@@@@@@@ */
    @Override
    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
        System.out.println("onDataChange method called");
        ArrayList<Post> posts=new ArrayList<>();
        for(DataSnapshot ds:dataSnapshot.getChildren()) {
            Post p=dataSnapshot.child(ds.getKey()).getValue(Post.class);
            posts.add(0,p);
        }
        progressBar.setVisibility(View.INVISIBLE);
        recyclerViewAdapter.setPosts(posts);
    }

    @Override
    public void onCancelled(@NonNull DatabaseError databaseError) {
        System.out.println("Error is new fetching data");
    }
    /* @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ */




    /* ####################### Overriden methods of Callback<HashMap<String,Post>> ############### */
    @Override
    public void onResponse(Call<HashMap<String, Post>> call, Response<HashMap<String, Post>> response) {
        HashMap<String,Post> hashMap=response.body();
        monthCount--;
        if(hashMap!=null) {
            System.out.println("onResponse hashmap : "+hashMap);
            ArrayList<Post> posts = new ArrayList<>();
            for (String key : hashMap.keySet()) {
                posts.add(0,hashMap.get(key));
                System.out.println(hashMap.get(key));
            }
            recyclerViewAdapter.removePost();//remove the null post
            recyclerViewAdapter.addPosts(posts);
            recyclerViewAdapter.setLoading(false);
        }
        else {
            System.out.println("hashmap is null");
            //necesary to remove the null post when no changes are made to dataset
            Calendar c=Calendar.getInstance();
            c.add(Calendar.MONTH,monthCount);
            if (c.get(Calendar.YEAR)>=2018) {
                homePageClient.getPosts("Y"+c.get(Calendar.YEAR),
                        "M"+c.get(Calendar.MONTH))
                        .enqueue(this);
            }
            else {
                recyclerViewAdapter.removePost();
                recyclerViewAdapter.setLoading(false);
            }
        }
    }

    @Override
    public void onFailure(Call<HashMap<String, Post>> call, Throwable t) {
        System.out.println("failed");
        t.printStackTrace();
        recyclerViewAdapter.removePost();
        recyclerViewAdapter.setLoading(false);

    }
    /* ######################################################################################### */

    public void onNewPostDataAvailable(Bundle args) {
        System.out.println("on new post data available called");
        ((HomeActivity)getContext()).getSupportActionBar().hide();
        ((HomeActivity)getContext()).setDrawerEnabled(false);
        ImageUploadFragment imageUploadFragment=ImageUploadFragment.newInstance(homePageClient,signedInMember.getMemberId());
        imageUploadFragment.setArguments(args);

        FragmentTransaction ft=((HomeActivity)getContext()).getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frame_layout,imageUploadFragment,getString(R.string.fragment_tag_image_upload));
        ft.commit();
    }



    @Override
    public void onLoadMore() {
        System.out.println("on load more");
        recyclerViewAdapter.setLoading(true);//keep this above the addPost
        recyclerViewAdapter.addPost(null);//place holder for the progress bar


        Calendar c=Calendar.getInstance();
        c.add(Calendar.MONTH,monthCount);

        homePageClient.getPosts("Y"+c.get(Calendar.YEAR),"M"+c.get(Calendar.MONTH))
                .enqueue(this);
    }


    @Override
    public void onMemberLogin(@NonNull Member signedInMember) {
        System.out.println("postfragment onMemberLogin : "+signedInMember);
        this.signedInMember=signedInMember;
        recyclerViewAdapter.setSignedInMember(signedInMember);
    }

    @Override
    public void onMemberLogout() {
        System.out.println("postfragment onMemberLogout : ");
        this.signedInMember=null;
        recyclerViewAdapter.setSignedInMember(null);
    }
}
