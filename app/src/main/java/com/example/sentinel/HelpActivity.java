package com.example.sentinel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class HelpActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private FirebaseAuth mAuth;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private ImageView ivUserProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupDrawer();
        loadUserProfile();
        setupButtons();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        // Get nav header views
        android.view.View headerView = navigationView.getHeaderView(0);
        tvUserName = headerView.findViewById(R.id.tv_user_name);
        tvUserEmail = headerView.findViewById(R.id.tv_user_email);
        ivUserProfile = headerView.findViewById(R.id.iv_user_profile);

        // Setup action bar toggle
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.drawer_open, R.string.drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Help & FAQ");
        }

        // Handle navigation item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            handleNavigationItemSelected(item);
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void setupDrawer() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadUserProfile() {
        com.google.firebase.auth.FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                tvUserName.setText(displayName);
            } else {
                tvUserName.setText("User");
            }

            String email = currentUser.getEmail();
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText(email);
            } else {
                tvUserEmail.setText("No email");
            }

            Uri photoUrl = currentUser.getPhotoUrl();
            if (photoUrl != null) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .circleCrop()
                        .into(ivUserProfile);
            } else {
                ivUserProfile.setImageResource(R.drawable.ic_user_placeholder);
            }
        }
    }

    private void setupButtons() {
        Button btnContactSupport = findViewById(R.id.btn_contact_support);

        btnContactSupport.setOnClickListener(v -> sendSupportEmail());
    }

    private void sendSupportEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:support@sentinelapp.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Sentinel App - Help Request");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Hi Sentinel Team,\n\nI need help with:\n\n");

        try {
            startActivity(Intent.createChooser(emailIntent, "Send help request via:"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            finish(); // Go back to MainActivity
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_history) {
            Intent intent = new Intent(this, AlertHistoryActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_help) {
            // Already on Help screen - do nothing
        } else if (id == R.id.nav_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_sign_out) {
            signOut();
        }
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(HelpActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}