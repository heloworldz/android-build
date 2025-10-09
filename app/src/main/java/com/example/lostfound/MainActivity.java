package com.lostandfound;
import com.lostandfound.Post;
import com.lostandfound.PostsAdapter;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton fabAddPost;
    private RecyclerView recyclerViewPosts;
    private PostsAdapter adapter;
    private List<Post> postList;

    private Uri selectedImageUri;
    private StorageReference storageReference;
    private FirebaseFirestore firestore;

    private ImageView imageViewPreviewDialog;
    private static final int REQUEST_CODE_PICK_IMAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        storageReference = FirebaseStorage.getInstance().getReference("post_images");

        fabAddPost = findViewById(R.id.fabAddPost);
        recyclerViewPosts = findViewById(R.id.recyclerViewPosts);

        postList = new ArrayList<>();
        adapter = new PostsAdapter(this, postList);
        recyclerViewPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPosts.setAdapter(adapter);

        fabAddPost.setOnClickListener(v -> showAddPostDialog());
        loadPosts();
    }

    private void loadPosts() {
        CollectionReference postsRef = firestore.collection("posts");
        postsRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Toast.makeText(MainActivity.this, "Error loading posts", Toast.LENGTH_SHORT).show();
                return;
            }
            postList.clear();
            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    Post post = doc.toObject(Post.class);
                    post.setId(doc.getId());
                    postList.add(post);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void showAddPostDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Lost/Found Item");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_post, null, false);
        builder.setView(viewInflated);

        Spinner spinnerType = viewInflated.findViewById(R.id.spinner_post_type);
        EditText editTextDescription = viewInflated.findViewById(R.id.editTextDescription);
        EditText editTextLocation = viewInflated.findViewById(R.id.editTextLocation);
        Button buttonSelectImage = viewInflated.findViewById(R.id.buttonSelectImage);
        imageViewPreviewDialog = viewInflated.findViewById(R.id.imageViewPreview);

        // Select image
        buttonSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        });

        builder.setPositiveButton("Post", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String type = spinnerType.getSelectedItem().toString();
                String description = editTextDescription.getText().toString().trim();
                String location = editTextLocation.getText().toString().trim();

                if (TextUtils.isEmpty(description)) {
                    editTextDescription.setError("Description required");
                    return;
                }
                if (TextUtils.isEmpty(location)) {
                    editTextLocation.setError("Location required");
                    return;
                }

                if (selectedImageUri != null) {
                    uploadImageAndSavePost(type, description, location, selectedImageUri, dialog);
                } else {
                    savePost(type, description, location, null, dialog);
                }
            });
        });

        dialog.show();
    }

    private void uploadImageAndSavePost(String type, String description, String location, Uri imageUri, AlertDialog dialog) {
        String fileName = UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageReference.child(fileName);

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> savePost(type, description, location, uri.toString(), dialog))
                        .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to get image URL", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show());
    }

    private void savePost(String type, String description, String location, String imageUrl, AlertDialog dialog) {
        String id = firestore.collection("posts").document().getId();
        Post post = new Post(id, type, description, location, imageUrl);

        firestore.collection("posts").document(id)
                .set(post)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Post added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to add post", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (imageViewPreviewDialog != null) {
                imageViewPreviewDialog.setVisibility(View.VISIBLE);
                imageViewPreviewDialog.setImageURI(selectedImageUri);
            }
            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
        }
    }
}



