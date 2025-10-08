package com.example.lostfoundnetwork;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton fabAddPost;
    private RecyclerView recyclerViewPosts;
    private PostsAdapter adapter;
    private List<Post> postList;

    private Uri selectedImageUri;
    private StorageReference storageReference;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		// Initialize Firestore & Storage
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

        postsRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot value,
                                @Nullable FirebaseFirestoreException error) {
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
            }
        });
    }

    private void showAddPostDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Lost/Found Item");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_post, null, false);
        builder.setView(viewInflated);

        Spinner spinnerType = viewInflated.findViewById(R.id.spinnerType);
        EditText editTextDescription = viewInflated.findViewById(R.id.editTextDescription);
        EditText editTextLocation = viewInflated.findViewById(R.id.editTextLocation);
        Button buttonSelectImage = viewInflated.findViewById(R.id.buttonSelectImage);
        ImageView imageViewPreview = viewInflated.findViewById(R.id.imageViewPreview);

        selectedImageUri = null;
        imageViewPreview.setVisibility(View.GONE);

        buttonSelectImage.setOnClickListener(v -> {
            ImagePicker.with(MainActivity.this)
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .start();
        });

        builder.setPositiveButton("Post", null); // Overridden below
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

                // Upload image if selected, then save post
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
                        .addOnSuccessListener(uri -> {
                            savePost(type, description, location, uri.toString(), dialog);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(MainActivity.this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                });
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
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Failed to add post", Toast.LENGTH_SHORT).show();
                });
    }

    // Handle image picker result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) return;

        selectedImageUri = data.getData();
        // Get reference to imageViewPreview inside dialog (find the current visible dialog's ImageView)
        AlertDialog currentDialog = (AlertDialog) getSupportFragmentManager().findFragmentByTag("AddPostDialog");
        // Instead, to update the ImageView preview, better approach is to keep it as member variables or use a custom dialog class.
        // For simplicity, here is a workaround:

        // Directly load image into ImageView preview if possible
        // Since we do not keep dialog references and variable scopes, use a Toast as notification for now

        Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();

        // Note: If you want to show image preview inside dialog after selection,
        // consider implementing the dialog with a DialogFragment for better control.
    }
}