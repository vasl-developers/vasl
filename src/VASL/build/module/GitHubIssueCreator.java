package VASL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.tools.menu.MenuItemProxy;
import VASSAL.tools.menu.MenuManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.StringReader;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.List;
import java.util.Base64;
import java.util.Date;
import java.security.interfaces.RSAPrivateKey;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
//import java.security.spec.PKCS1EncodedKeySpec;
import java.security.interfaces.RSAPrivateKey;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Adds an entry to the Help menu that allows users to submit suggestions
 * as GitHub issues directly from within the VASSAL game.
 */
public class GitHubIssueCreator extends AbstractConfigurable {
    // Configuration Keys
    public static final String TITLE = "title";
    public static final String REPO_OWNER = "vasl-developers";
    public static final String REPO_NAME = "vasl";

    // Extract GitHub App credentials from environment variables (set these in your GitHub Secrets)
    private static final String GITHUB_APP_ID = "1186007"; // Expected value: "1186007"
    private static final String GITHUB_CLIENT_ID = "Iv23liEIS38vnDddlHvJ";// Expected value: "Iv23liEIS38vnDddlHvJ"
    private static final String GITHUB_PRIVATE_KEY = System.getenv("GITHUB_PRIVATE_KEY"); // Your private key string stored as a secret

    // Default Values
    private String title = "Submit Suggestion";
    private String repoOwner = "vasl-developers";
    private String repoName = "vasl";

    private Action launch;
    private MenuItemProxy launchItem;

    public GitHubIssueCreator() {
        setConfigureName(title);
        launch = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                showIssueDialog();
            }
        };

        launch.putValue(Action.NAME, getConfigureName());
    }

    /**
     * Displays a dialog for the user to input issue details.
     */
    private void showIssueDialog() {
        boolean valid = false; // Control flag

        String[] issueTypes = { "Improvement Suggestion", "Bug", "Other" };
        JComboBox<String> typeComboBox = new JComboBox<>(issueTypes);
        JTextField titleField = new JTextField(20);
        JTextArea descriptionArea = new JTextArea(10, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        JTextField emailField = new JTextField(20);
        emailField.setToolTipText("Enter your email if you wish to receive updates from the developers.");

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel typeLabel = new JLabel("Issue Type:");
        JLabel titleLabel = new JLabel("Title:");
        JLabel descriptionLabel = new JLabel("Description:");
        JLabel emailLabel = new JLabel("Your Email (optional):");

        // Issue Type
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        mainPanel.add(typeLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(typeComboBox, gbc);

        // Title
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        mainPanel.add(titleLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(titleField, gbc);

        // Description
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        mainPanel.add(descriptionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(descriptionScrollPane, gbc);

        // Email
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(emailLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(emailField, gbc);

        while (!valid) {
            int result = JOptionPane.showConfirmDialog(null, mainPanel, "Submit a Suggestion",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String issueType = (String) typeComboBox.getSelectedItem();
                String issueTitle = titleField.getText().trim();
                String issueBody = descriptionArea.getText().trim();
                String userEmail = emailField.getText().trim();

                if (issueTitle.isEmpty() || issueBody.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Title and Description cannot be empty.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                if (!userEmail.isEmpty() && !isValidEmail(userEmail)) {
                    JOptionPane.showMessageDialog(null, "Please enter a valid email address.", "Invalid Email",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                if (issueTitle.length() > 100) {
                    JOptionPane.showMessageDialog(null, "Title cannot exceed 100 characters.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                if (issueBody.length() > 1000) {
                    JOptionPane.showMessageDialog(null, "Description cannot exceed 1000 characters.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }

                StringBuilder enhancedBodyBuilder = new StringBuilder();
                enhancedBodyBuilder.append("**Issue Type:** ").append(issueType).append("\n\n");
                enhancedBodyBuilder.append(issueBody);
                if (!userEmail.isEmpty()) {
                    enhancedBodyBuilder.append("\n\n**Contact Email:** ").append(userEmail);
                }
                String enhancedBody = enhancedBodyBuilder.toString();
                List<String> labels = List.of(issueType);

                boolean submitted = submitGitHubIssue(issueTitle, enhancedBody, labels);

                if (submitted) {
                    JOptionPane.showMessageDialog(null,
                            "Your suggestion has been submitted successfully! Developers may contact you via your provided email.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    valid = true;
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Failed to submit your suggestion. Please check your internet connection or try again later.",
                            "Submission Failed", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                break;
            }
        }
    }

    /**
     * Validates email format.
     */
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    /**
     * Submits a GitHub issue via the GitHub REST API using GitHub App authentication.
     */
    private boolean submitGitHubIssue(String title, String body, List<String> labels) {
        OkHttpClient client = new OkHttpClient();

        // Step 1: Generate a JWT using the GitHub App credentials.
        String jwt = generateJwt();
        if (jwt == null || jwt.isEmpty()) {
            System.err.println("Failed to generate JWT.");
            return false;
        }

        // Step 2: Retrieve the installation ID for this repository.
        String installationId = getInstallationId(jwt);
        if (installationId == null) {
            System.err.println("Failed to retrieve installation ID.");
            return false;
        }

        // Step 3: Request an installation access token.
        String installationToken = getInstallationAccessToken(jwt, installationId);
        if (installationToken == null) {
            System.err.println("Failed to retrieve installation access token.");
            return false;
        }

        // Build JSON payload for creating the issue.
        JSONObject json = new JSONObject();
        json.put("title", title);
        json.put("body", body);
        if (labels != null && !labels.isEmpty()) {
            JSONArray labelsArray = new JSONArray();
            for (String label : labels) {
                labelsArray.put(label);
            }
            json.put("labels", labelsArray);
        }

        RequestBody requestBody = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        // Use the installation token to authenticate the issue creation request.
        Request request = new Request.Builder()
                .url(String.format("https://api.github.com/repos/%s/%s/issues", repoOwner, repoName))
                .header("Authorization", "token " + installationToken)
                .header("Accept", "application/vnd.github.v3+json")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            } else {
                System.err.println("Failed to create issue: " + response.code() + " " + response.message());
                String responseBody = response.body() != null ? response.body().string() : "";
                System.err.println("Response Body: " + responseBody);
                return false;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while submitting your suggestion.\nError: " + e.getMessage(),
                    "Submission Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Generates a JWT for GitHub App authentication.
     *
     */
    private String generateJwt() {
        long now = System.currentTimeMillis();
        long exp = now + 600 * 1000; // 10 minutes validity

        if (GITHUB_PRIVATE_KEY == null || GITHUB_PRIVATE_KEY.isEmpty()) {
            System.err.println("Error: GitHub private key is not set. Please ensure the GITHUB_PRIVATE_KEY environment variable is configured.");
            return null;
        }

        try {
            // Load your private key from the string
            RSAPrivateKey privateKey = loadPrivateKey(GITHUB_PRIVATE_KEY);

            // Create the JWT token
            Algorithm algorithm = Algorithm.RSA256(null, privateKey);
            return JWT.create()
                    .withIssuedAt(new Date(now))
                    .withExpiresAt(new Date(exp))
                    .withIssuer(GITHUB_APP_ID)
                    .sign(algorithm);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private RSAPrivateKey loadPrivateKey(String privateKeyPem) throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
        PEMParser pemParser = new PEMParser(new StringReader(privateKeyPem));
        Object object = pemParser.readObject();
        pemParser.close();

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        if (object instanceof PEMKeyPair) {
            PEMKeyPair keyPair = (PEMKeyPair) object;
            PrivateKeyInfo privateKeyInfo = keyPair.getPrivateKeyInfo();
            return (RSAPrivateKey) converter.getPrivateKey(privateKeyInfo);
        } else {
            throw new InvalidKeySpecException("Unsupported key format: " + object.getClass().getName());
        }
    }

    /**
     * Retrieves the installation ID for the given repository.
     */
    private String getInstallationId(String jwt) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(String.format("https://api.github.com/repos/%s/%s/installation", repoOwner, repoName))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject jsonResponse = new JSONObject(responseBody);
                return jsonResponse.optString("id", null);
            } else {
                System.err.println("Failed to get installation info: " + response.code() + " " + response.message());
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Retrieves an installation access token using the GitHub App JWT and installation ID.
     */
    private String getInstallationAccessToken(String jwt, String installationId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(String.format("https://api.github.com/app/installations/%s/access_tokens", installationId))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github.v3+json")
                .post(RequestBody.create(new byte[0], null)) // Empty POST body required.
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject jsonResponse = new JSONObject(responseBody);
                return jsonResponse.optString("token", null);
            } else {
                System.err.println("Failed to get installation access token: " + response.code() + " " + response.message());
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    @Override
    public String[] getAttributeNames() {
        return new String[]{
                TITLE,
                REPO_OWNER,
                REPO_NAME
                // No token attribute required since we use GitHub App credentials.
        };
    }

    @Override
    public String getAttributeValueString(String key) {
        if (key.equals(TITLE)) {
            return title;
        } else if (key.equals(REPO_OWNER)) {
            return repoOwner;
        } else if (key.equals(REPO_NAME)) {
            return repoName;
        } else {
            return null;
        }
    }

    @Override
    public void setAttribute(String key, Object val) {
        if (key.equals(TITLE)) {
            title = (String) val;
            setConfigureName(title);
            launch.putValue(Action.NAME, title);
        } else if (key.equals(REPO_OWNER)) {
            repoOwner = (String) val;
        } else if (key.equals(REPO_NAME)) {
            repoName = (String) val;
        }
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{
                "The text of the menu entry.",
                "GitHub repository owner (username or organization).",
                "GitHub repository name."
        };
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
                String.class,
                String.class,
                String.class
        };
    }

    @Override
    public Class<?>[] getAllowableConfigureComponents() {
        return new Class<?>[0];
    }

    @Override
    public void addTo(Buildable b) {
        launchItem = new MenuItemProxy(launch);
        MenuManager.getInstance().addToSection("Documentation.Module", launchItem);
        launch.setEnabled(true);
    }

    @Override
    public void removeFrom(Buildable b) {
        MenuManager.getInstance().removeFromSection("Documentation.Module", launchItem);
        launch.setEnabled(false);
    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public List<String> getFormattedStringList() {
        return List.of(title);
    }

    @Override
    public void addLocalImageNames(Collection<String> s) {
        // No images to add
    }
}

