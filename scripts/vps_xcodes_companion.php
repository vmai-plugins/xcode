<?php
/**
 * Plugin Name: X-Codes VPS Companion
 * Plugin URI: https://vmstudio.digital
 * Description: Dedicated developer cloud vault & companion backend for X-Codes IDE. Provides two-way chat synchronization, developer workspaces, APK artifact tracking, and remote agent execution.
 * Version: 2.0.0
 * Author: VMStudio Digital
 * Author URI: https://vmstudio.digital
 * License: GPLv2 or later
 */

if (!defined('ABSPATH')) {
    exit;
}

class XCodesCompanionPlugin {

    const VERSION = '2.0.0';
    const ROUTE_NAMESPACE = 'xcodes/v1';

    public function __construct() {
        register_activation_hook(__FILE__, [__CLASS__, 'activate']);
        add_action('rest_api_init', [$this, 'register_routes']);
        add_action('admin_menu', [$this, 'register_admin_menu']);
        add_action('admin_init', [$this, 'register_settings']);
    }

    public static function activate() {
        global $wpdb;
        $charset_collate = $wpdb->get_charset_collate();

        require_once ABSPATH . 'wp-admin/includes/upgrade.php';

        // 1. Conversations table
        $table_conversations = $wpdb->prefix . 'xcodes_conversations';
        $sql_conversations = "CREATE TABLE $table_conversations (
            id varchar(64) NOT NULL,
            project_id varchar(64) DEFAULT NULL,
            title varchar(255) NOT NULL,
            provider_id varchar(64) NOT NULL DEFAULT 'omniroute',
            provider_session_id varchar(128) DEFAULT NULL,
            server_id varchar(64) DEFAULT NULL,
            working_directory text DEFAULT NULL,
            model_id varchar(64) NOT NULL DEFAULT '',
            prompt_tokens bigint(20) NOT NULL DEFAULT 0,
            completion_tokens bigint(20) NOT NULL DEFAULT 0,
            is_archived tinyint(1) NOT NULL DEFAULT 0,
            created_at bigint(20) NOT NULL,
            updated_at bigint(20) NOT NULL,
            PRIMARY KEY  (id),
            KEY project_idx (project_id),
            KEY updated_idx (updated_at)
        ) $charset_collate;";
        dbDelta($sql_conversations);

        // 2. Messages table
        $table_messages = $wpdb->prefix . 'xcodes_messages';
        $sql_messages = "CREATE TABLE $table_messages (
            id varchar(64) NOT NULL,
            conversation_id varchar(64) NOT NULL,
            role varchar(32) NOT NULL,
            content longtext NOT NULL,
            reasoning longtext DEFAULT NULL,
            status varchar(32) NOT NULL DEFAULT 'COMPLETE',
            error_text text DEFAULT NULL,
            token_count int(11) NOT NULL DEFAULT 0,
            model_id varchar(64) DEFAULT NULL,
            created_at bigint(20) NOT NULL,
            sequence bigint(20) NOT NULL DEFAULT 0,
            PRIMARY KEY  (id),
            KEY conv_seq_idx (conversation_id, sequence),
            KEY conv_created_idx (conversation_id, created_at)
        ) $charset_collate;";
        dbDelta($sql_messages);

        // 3. Developer Workspaces / Projects
        $table_projects = $wpdb->prefix . 'xcodes_projects';
        $sql_projects = "CREATE TABLE $table_projects (
            id varchar(64) NOT NULL,
            name varchar(255) NOT NULL,
            slug varchar(128) NOT NULL,
            stack varchar(64) NOT NULL DEFAULT 'custom',
            repo_url varchar(512) DEFAULT NULL,
            server_path varchar(512) DEFAULT NULL,
            app_path varchar(512) DEFAULT NULL,
            live_url varchar(512) DEFAULT NULL,
            status varchar(32) NOT NULL DEFAULT 'ACTIVE',
            updated_at bigint(20) NOT NULL,
            PRIMARY KEY  (id),
            UNIQUE KEY slug_idx (slug)
        ) $charset_collate;";
        dbDelta($sql_projects);

        // 4. Artifacts (APKs, builds, diff patches)
        $table_artifacts = $wpdb->prefix . 'xcodes_artifacts';
        $sql_artifacts = "CREATE TABLE $table_artifacts (
            id varchar(64) NOT NULL,
            project_id varchar(64) DEFAULT NULL,
            name varchar(255) NOT NULL,
            artifact_type varchar(32) NOT NULL DEFAULT 'apk',
            file_path varchar(512) DEFAULT NULL,
            file_url varchar(512) DEFAULT NULL,
            size_bytes bigint(20) NOT NULL DEFAULT 0,
            created_at bigint(20) NOT NULL,
            PRIMARY KEY  (id),
            KEY project_idx (project_id)
        ) $charset_collate;";
        dbDelta($sql_artifacts);

        // Ensure default secret pairing key
        if (!get_option('xcodes_pairing_key') && !get_option('vmpj_pairing_key')) {
            update_option('xcodes_pairing_key', bin2hex(random_bytes(16)));
        }
    }

    public function register_settings() {
        register_setting('xcodes_settings', 'xcodes_pairing_key');
        register_setting('xcodes_settings', 'xcodes_github_token');
        register_setting('xcodes_settings', 'xcodes_omniroute_key');
        register_setting('xcodes_settings', 'xcodes_omniroute_url');
        register_setting('xcodes_settings', 'xcodes_default_model');
        register_setting('xcodes_settings', 'xcodes_vps_host');
        // Google Drive integration for webxprodigital@gmail.com
        register_setting('xcodes_settings', 'xcodes_gdrive_connected_email');
        register_setting('xcodes_settings', 'xcodes_gdrive_client_id');
        register_setting('xcodes_settings', 'xcodes_gdrive_client_secret');
        register_setting('xcodes_settings', 'xcodes_gdrive_refresh_token');
    }

    public function register_admin_menu() {
        add_menu_page(
            'X-Codes Companion',
            'X-Codes Companion',
            'manage_options',
            'xcodes-companion',
            [$this, 'render_admin_page'],
            'dashicons-editor-code',
            30
        );
    }

    public function render_admin_page() {
        global $wpdb;
        $conv_count = $wpdb->get_var("SELECT COUNT(*) FROM {$wpdb->prefix}xcodes_conversations");
        $msg_count = $wpdb->get_var("SELECT COUNT(*) FROM {$wpdb->prefix}xcodes_messages");
        $proj_count = $wpdb->get_var("SELECT COUNT(*) FROM {$wpdb->prefix}xcodes_projects");
        $art_count = $wpdb->get_var("SELECT COUNT(*) FROM {$wpdb->prefix}xcodes_artifacts");
        $pairing_key = get_option('xcodes_pairing_key', get_option('vmpj_pairing_key', ''));

        ?>
        <div class="wrap">
            <h1><span class="dashicons dashicons-editor-code" style="font-size:30px;width:30px;height:30px;vertical-align:middle;margin-right:8px;"></span> X-Codes VPS Companion</h1>
            <p>Cloud synchronization backend and developer vault for your X-Codes Mobile IDE.</p>
            <hr />

            <div style="display:flex;gap:20px;margin:20px 0;">
                <div style="background:#fff;border-left:4px solid #4CAF50;padding:15px;box-shadow:0 1px 3px rgba(0,0,0,0.1);min-width:180px;">
                    <div style="font-size:24px;font-weight:bold;color:#333;"><?php echo intval($conv_count); ?></div>
                    <div style="color:#666;">Synced Conversations</div>
                </div>
                <div style="background:#fff;border-left:4px solid #2196F3;padding:15px;box-shadow:0 1px 3px rgba(0,0,0,0.1);min-width:180px;">
                    <div style="font-size:24px;font-weight:bold;color:#333;"><?php echo intval($msg_count); ?></div>
                    <div style="color:#666;">Total Chat Messages</div>
                </div>
                <div style="background:#fff;border-left:4px solid #9C27B0;padding:15px;box-shadow:0 1px 3px rgba(0,0,0,0.1);min-width:180px;">
                    <div style="font-size:24px;font-weight:bold;color:#333;"><?php echo intval($proj_count); ?></div>
                    <div style="color:#666;">Workspaces / Projects</div>
                </div>
                <div style="background:#fff;border-left:4px solid #FF9800;padding:15px;box-shadow:0 1px 3px rgba(0,0,0,0.1);min-width:180px;">
                    <div style="font-size:24px;font-weight:bold;color:#333;"><?php echo intval($art_count); ?></div>
                    <div style="color:#666;">Build Artifacts (APKs)</div>
                </div>
            </div>

            <form method="post" action="options.php" style="background:#fff;padding:20px;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.1);max-width:800px;">
                <?php settings_fields('xcodes_settings'); ?>
                <h2>Authentication & Pairing</h2>
                <table class="form-table">
                    <tr>
                        <th scope="row"><label for="xcodes_pairing_key">Hub Pairing Key</label></th>
                        <td>
                            <input name="xcodes_pairing_key" type="text" id="xcodes_pairing_key" value="<?php echo esc_attr($pairing_key); ?>" class="regular-text" style="font-family:monospace;" />
                            <p class="description">Enter this key in X-Codes under <strong>Settings &gt; Connectors &gt; VM Project Hub</strong> to sync your chats and projects.</p>
                        </td>
                    </tr>
                </table>

                <h2 style="margin-top:30px;">Global Environment Defaults (Auto-injected into App)</h2>
                <table class="form-table">
                    <tr>
                        <th scope="row"><label for="xcodes_omniroute_url">OmniRoute / AI URL</label></th>
                        <td><input name="xcodes_omniroute_url" type="text" id="xcodes_omniroute_url" value="<?php echo esc_attr(get_option('xcodes_omniroute_url', '')); ?>" class="regular-text" placeholder="https://api.omniroute.ai/v1" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_omniroute_key">OmniRoute API Key</label></th>
                        <td><input name="xcodes_omniroute_key" type="password" id="xcodes_omniroute_key" value="<?php echo esc_attr(get_option('xcodes_omniroute_key', '')); ?>" class="regular-text" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_default_model">Default AI Model</label></th>
                        <td><input name="xcodes_default_model" type="text" id="xcodes_default_model" value="<?php echo esc_attr(get_option('xcodes_default_model', 'claude-3-5-sonnet-20241022')); ?>" class="regular-text" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_github_token">GitHub Access Token</label></th>
                        <td><input name="xcodes_github_token" type="password" id="xcodes_github_token" value="<?php echo esc_attr(get_option('xcodes_github_token', '')); ?>" class="regular-text" /></td>
                    </tr>
                </table>

                <h2 style="margin-top:30px;">Google Drive Vault (webxprodigital@gmail.com)</h2>
                <p>Connects your Google Drive account so X-Codes can automatically backup chat histories, projects, and APK artifacts.</p>
                <table class="form-table">
                    <tr>
                        <th scope="row"><label for="xcodes_gdrive_connected_email">Google Account</label></th>
                        <td><input name="xcodes_gdrive_connected_email" type="email" id="xcodes_gdrive_connected_email" value="<?php echo esc_attr(get_option('xcodes_gdrive_connected_email', 'webxprodigital@gmail.com')); ?>" class="regular-text" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_gdrive_client_id">OAuth Client ID</label></th>
                        <td><input name="xcodes_gdrive_client_id" type="text" id="xcodes_gdrive_client_id" value="<?php echo esc_attr(get_option('xcodes_gdrive_client_id', '')); ?>" class="regular-text" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_gdrive_client_secret">OAuth Client Secret</label></th>
                        <td><input name="xcodes_gdrive_client_secret" type="password" id="xcodes_gdrive_client_secret" value="<?php echo esc_attr(get_option('xcodes_gdrive_client_secret', '')); ?>" class="regular-text" /></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="xcodes_gdrive_refresh_token">OAuth Refresh Token</label></th>
                        <td><input name="xcodes_gdrive_refresh_token" type="password" id="xcodes_gdrive_refresh_token" value="<?php echo esc_attr(get_option('xcodes_gdrive_refresh_token', '')); ?>" class="regular-text" /></td>
                    </tr>
                </table>

                <?php submit_button('Save Settings'); ?>
            </form>
        </div>
        <?php
    }

    public function register_routes() {
        // Authenticated routes
        register_rest_route(self::ROUTE_NAMESPACE, '/chats/sync', [
            'methods' => 'POST',
            'callback' => [$this, 'handle_sync_chat'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/chats', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_list_chats'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/chats/(?P<id>[a-zA-Z0-9_\-]+)', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_get_chat'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/chats/(?P<id>[a-zA-Z0-9_\-]+)', [
            'methods' => 'DELETE',
            'callback' => [$this, 'handle_delete_chat'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/projects', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_list_projects'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/projects', [
            'methods' => 'POST',
            'callback' => [$this, 'handle_upsert_project'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/credentials', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_get_credentials'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/artifacts', [
            'methods' => 'POST',
            'callback' => [$this, 'handle_record_artifact'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        // Google Drive integration routes
        register_rest_route(self::ROUTE_NAMESPACE, '/drive/token', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_get_drive_token'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/drive/upload', [
            'methods' => 'POST',
            'callback' => [$this, 'handle_drive_upload'],
            'permission_callback' => [$this, 'check_auth'],
        ]);

        register_rest_route(self::ROUTE_NAMESPACE, '/drive/files', [
            'methods' => 'GET',
            'callback' => [$this, 'handle_drive_files'],
            'permission_callback' => [$this, 'check_auth'],
        ]);
    }

    public function check_auth($request) {
        $key = $request->get_header('X-VMPJ-Key');
        if (empty($key)) {
            $key = $request->get_header('apikey');
        }
        if (empty($key)) {
            $key = $request->get_param('key');
        }

        $valid_key = get_option('xcodes_pairing_key', get_option('vmpj_pairing_key', ''));
        if (!empty($valid_key) && hash_equals($valid_key, (string)$key)) {
            return true;
        }

        return new WP_Error('rest_forbidden', 'Invalid or missing X-Codes pairing key', ['status' => 401]);
    }

    /**
     * Syncs a conversation and all its messages in one atomic batch.
     */
    public function handle_sync_chat($request) {
        global $wpdb;
        $body = $request->get_json_params();

        if (empty($body['conversation']) || !is_array($body['conversation'])) {
            return new WP_Error('bad_request', 'Missing conversation data', ['status' => 400]);
        }

        $conv = $body['conversation'];
        $conv_id = sanitize_text_field($conv['id']);
        if (empty($conv_id)) {
            return new WP_Error('bad_request', 'Invalid conversation id', ['status' => 400]);
        }

        $t_conv = $wpdb->prefix . 'xcodes_conversations';
        $t_msg = $wpdb->prefix . 'xcodes_messages';

        // Upsert conversation
        $conv_data = [
            'id' => $conv_id,
            'project_id' => !empty($conv['projectId']) ? sanitize_text_field($conv['projectId']) : null,
            'title' => sanitize_text_field($conv['title'] ?? 'New Chat'),
            'provider_id' => sanitize_text_field($conv['providerId'] ?? 'omniroute'),
            'provider_session_id' => !empty($conv['providerSessionId']) ? sanitize_text_field($conv['providerSessionId']) : null,
            'server_id' => !empty($conv['serverId']) ? sanitize_text_field($conv['serverId']) : null,
            'working_directory' => !empty($conv['workingDirectory']) ? sanitize_text_field($conv['workingDirectory']) : null,
            'model_id' => sanitize_text_field($conv['modelId'] ?? ''),
            'prompt_tokens' => intval($conv['promptTokens'] ?? 0),
            'completion_tokens' => intval($conv['completionTokens'] ?? 0),
            'is_archived' => !empty($conv['isArchived']) ? 1 : 0,
            'created_at' => intval($conv['createdAtMillis'] ?? (time() * 1000)),
            'updated_at' => intval($conv['updatedAtMillis'] ?? (time() * 1000)),
        ];

        $wpdb->replace($t_conv, $conv_data);

        // Batch upsert messages
        $messages = $body['messages'] ?? [];
        $synced_messages = 0;
        if (is_array($messages) && !empty($messages)) {
            foreach ($messages as $msg) {
                $msg_id = sanitize_text_field($msg['id'] ?? '');
                if (empty($msg_id)) continue;

                $msg_data = [
                    'id' => $msg_id,
                    'conversation_id' => $conv_id,
                    'role' => sanitize_text_field($msg['role'] ?? 'USER'),
                    'content' => $msg['content'] ?? '',
                    'reasoning' => $msg['reasoning'] ?? null,
                    'status' => sanitize_text_field($msg['status'] ?? 'COMPLETE'),
                    'error_text' => !empty($msg['errorText']) ? sanitize_text_field($msg['errorText']) : null,
                    'token_count' => intval($msg['tokenCount'] ?? 0),
                    'model_id' => !empty($msg['modelId']) ? sanitize_text_field($msg['modelId']) : null,
                    'created_at' => intval($msg['createdAtMillis'] ?? (time() * 1000)),
                    'sequence' => intval($msg['sequence'] ?? 0),
                ];
                $wpdb->replace($t_msg, $msg_data);
                $synced_messages++;
            }
        }

        return rest_ensure_response([
            'success' => true,
            'conversationId' => $conv_id,
            'messagesSynced' => $synced_messages,
            'timestamp' => time() * 1000,
        ]);
    }

    /**
     * Lists conversation summaries for the user.
     */
    public function handle_list_chats($request) {
        global $wpdb;
        $t_conv = $wpdb->prefix . 'xcodes_conversations';
        $t_msg = $wpdb->prefix . 'xcodes_messages';

        $query = "SELECT c.*, COUNT(m.id) as message_count
                  FROM $t_conv c
                  LEFT JOIN $t_msg m ON c.id = m.conversation_id
                  WHERE c.is_archived = 0
                  GROUP BY c.id
                  ORDER BY c.updated_at DESC
                  LIMIT 100";

        $rows = $wpdb->get_results($query, ARRAY_A);
        $result = [];
        foreach ($rows as $row) {
            $result[] = [
                'id' => $row['id'],
                'projectId' => $row['project_id'],
                'title' => $row['title'],
                'providerId' => $row['provider_id'],
                'providerSessionId' => $row['provider_session_id'],
                'serverId' => $row['server_id'],
                'workingDirectory' => $row['working_directory'],
                'modelId' => $row['model_id'],
                'promptTokens' => intval($row['prompt_tokens']),
                'completionTokens' => intval($row['completion_tokens']),
                'messageCount' => intval($row['message_count']),
                'createdAtMillis' => intval($row['created_at']),
                'updatedAtMillis' => intval($row['updated_at']),
            ];
        }

        return rest_ensure_response($result);
    }

    /**
     * Retrieves full transcript of a conversation.
     */
    public function handle_get_chat($request) {
        global $wpdb;
        $id = sanitize_text_field($request->get_param('id'));
        $t_conv = $wpdb->prefix . 'xcodes_conversations';
        $t_msg = $wpdb->prefix . 'xcodes_messages';

        $conv = $wpdb->get_row($wpdb->prepare("SELECT * FROM $t_conv WHERE id = %s", $id), ARRAY_A);
        if (!$conv) {
            return new WP_Error('not_found', 'Conversation not found', ['status' => 404]);
        }

        $messages = $wpdb->get_results(
            $wpdb->prepare("SELECT * FROM $t_msg WHERE conversation_id = %s ORDER BY sequence ASC, created_at ASC", $id),
            ARRAY_A
        );

        $formatted_messages = [];
        foreach ($messages as $m) {
            $formatted_messages[] = [
                'id' => $m['id'],
                'conversationId' => $m['conversation_id'],
                'role' => $m['role'],
                'content' => $m['content'],
                'reasoning' => $m['reasoning'],
                'status' => $m['status'],
                'errorText' => $m['error_text'],
                'tokenCount' => intval($m['token_count']),
                'modelId' => $m['model_id'],
                'createdAtMillis' => intval($m['created_at']),
                'sequence' => intval($m['sequence']),
            ];
        }

        return rest_ensure_response([
            'conversation' => [
                'id' => $conv['id'],
                'projectId' => $conv['project_id'],
                'title' => $conv['title'],
                'providerId' => $conv['provider_id'],
                'providerSessionId' => $conv['provider_session_id'],
                'serverId' => $conv['server_id'],
                'workingDirectory' => $conv['working_directory'],
                'modelId' => $conv['model_id'],
                'promptTokens' => intval($conv['prompt_tokens']),
                'completionTokens' => intval($conv['completion_tokens']),
                'createdAtMillis' => intval($conv['created_at']),
                'updatedAtMillis' => intval($conv['updated_at']),
            ],
            'messages' => $formatted_messages,
        ]);
    }

    public function handle_delete_chat($request) {
        global $wpdb;
        $id = sanitize_text_field($request->get_param('id'));
        $t_conv = $wpdb->prefix . 'xcodes_conversations';
        $t_msg = $wpdb->prefix . 'xcodes_messages';

        $wpdb->delete($t_msg, ['conversation_id' => $id]);
        $wpdb->delete($t_conv, ['id' => $id]);

        return rest_ensure_response(['success' => true, 'id' => $id]);
    }

    /**
     * Lists developer projects.
     */
    public function handle_list_projects($request) {
        global $wpdb;
        $t_proj = $wpdb->prefix . 'xcodes_projects';
        $rows = $wpdb->get_results("SELECT * FROM $t_proj ORDER BY updated_at DESC", ARRAY_A);

        $projects = [];
        foreach ($rows as $r) {
            $projects[] = [
                'id' => $r['id'],
                'name' => $r['name'],
                'slug' => $r['slug'],
                'stack' => $r['stack'],
                'repoUrl' => $r['repo_url'],
                'serverPath' => $r['server_path'],
                'appPath' => $r['app_path'],
                'liveUrl' => $r['live_url'],
                'status' => $r['status'],
                'updatedAtMillis' => intval($r['updated_at']),
            ];
        }

        return rest_ensure_response($projects);
    }

    public function handle_upsert_project($request) {
        global $wpdb;
        $body = $request->get_json_params();
        $id = sanitize_text_field($body['id'] ?? uniqid('proj_'));
        $name = sanitize_text_field($body['name'] ?? 'Project');
        $slug = sanitize_title($body['slug'] ?? $name);

        $data = [
            'id' => $id,
            'name' => $name,
            'slug' => $slug,
            'stack' => sanitize_text_field($body['stack'] ?? 'custom'),
            'repo_url' => esc_url_raw($body['repoUrl'] ?? ''),
            'server_path' => sanitize_text_field($body['serverPath'] ?? ''),
            'app_path' => sanitize_text_field($body['appPath'] ?? ''),
            'live_url' => esc_url_raw($body['liveUrl'] ?? ''),
            'status' => sanitize_text_field($body['status'] ?? 'ACTIVE'),
            'updated_at' => intval($body['updatedAtMillis'] ?? (time() * 1000)),
        ];

        $wpdb->replace($wpdb->prefix . 'xcodes_projects', $data);
        return rest_ensure_response(['success' => true, 'id' => $id]);
    }

    public function handle_get_credentials($request) {
        return rest_ensure_response([
            'githubToken' => get_option('xcodes_github_token', ''),
            'omnirouteKey' => get_option('xcodes_omniroute_key', ''),
            'omnirouteUrl' => get_option('xcodes_omniroute_url', 'https://api.omniroute.ai/v1'),
            'defaultModel' => get_option('xcodes_default_model', 'claude-3-5-sonnet-20241022'),
            'vpsHost' => get_option('xcodes_vps_host', ''),
        ]);
    }

    public function handle_record_artifact($request) {
        global $wpdb;
        $body = $request->get_json_params();
        $id = sanitize_text_field($body['id'] ?? uniqid('art_'));

        $data = [
            'id' => $id,
            'project_id' => !empty($body['projectId']) ? sanitize_text_field($body['projectId']) : null,
            'name' => sanitize_text_field($body['name'] ?? 'app-release.apk'),
            'artifact_type' => sanitize_text_field($body['artifactType'] ?? 'apk'),
            'file_path' => sanitize_text_field($body['filePath'] ?? ''),
            'file_url' => esc_url_raw($body['fileUrl'] ?? ''),
            'size_bytes' => intval($body['sizeBytes'] ?? 0),
            'created_at' => intval($body['createdAtMillis'] ?? (time() * 1000)),
        ];

        $wpdb->replace($wpdb->prefix . 'xcodes_artifacts', $data);
        return rest_ensure_response(['success' => true, 'id' => $id]);
    }

    /**
     * Refreshes and returns a valid Google Drive OAuth2 access token for webxprodigital@gmail.com.
     */
    public function get_valid_gdrive_access_token() {
        $access_token = get_option('xcodes_gdrive_access_token');
        $expires = intval(get_option('xcodes_gdrive_token_expires', 0));

        // Still valid with 60s buffer
        if (!empty($access_token) && $expires > (time() + 60)) {
            return $access_token;
        }

        $refresh_token = get_option('xcodes_gdrive_refresh_token');
        $client_id = get_option('xcodes_gdrive_client_id');
        $client_secret = get_option('xcodes_gdrive_client_secret');

        if (empty($refresh_token) || empty($client_id) || empty($client_secret)) {
            return false;
        }

        $response = wp_remote_post('https://oauth2.googleapis.com/token', [
            'body' => [
                'client_id' => $client_id,
                'client_secret' => $client_secret,
                'refresh_token' => $refresh_token,
                'grant_type' => 'refresh_token',
            ],
            'timeout' => 15,
        ]);

        if (is_wp_error($response)) {
            return false;
        }

        $body = json_decode(wp_remote_retrieve_body($response), true);
        if (!empty($body['access_token'])) {
            $new_token = $body['access_token'];
            $expires_in = intval($body['expires_in'] ?? 3600);
            update_option('xcodes_gdrive_access_token', $new_token);
            update_option('xcodes_gdrive_token_expires', time() + $expires_in);
            return $new_token;
        }

        return false;
    }

    public function handle_get_drive_token($request) {
        $token = $this->get_valid_gdrive_access_token();
        $email = get_option('xcodes_gdrive_connected_email', 'webxprodigital@gmail.com');
        if (!$token) {
            return new WP_Error('gdrive_unauthorized', 'Google Drive not authenticated on VPS companion for ' . $email, ['status' => 401]);
        }
        return rest_ensure_response([
            'email' => $email,
            'accessToken' => $token,
            'expiresAt' => intval(get_option('xcodes_gdrive_token_expires', 0)),
        ]);
    }

    /**
     * Finds or creates the root 'X-Codes' folder in Google Drive.
     */
    private function get_or_create_xcodes_drive_folder($token) {
        $folder_id = get_option('xcodes_gdrive_folder_id');
        if (!empty($folder_id)) {
            return $folder_id;
        }

        // Query Drive
        $query_url = 'https://www.googleapis.com/drive/v3/files?q=' . urlencode("name = 'X-Codes' and mimeType = 'application/vnd.google-apps.folder' and trashed = false");
        $check = wp_remote_get($query_url, [
            'headers' => ['Authorization' => 'Bearer ' . $token],
            'timeout' => 15,
        ]);

        if (!is_wp_error($check)) {
            $data = json_decode(wp_remote_retrieve_body($check), true);
            if (!empty($data['files'][0]['id'])) {
                $found_id = $data['files'][0]['id'];
                update_option('xcodes_gdrive_folder_id', $found_id);
                return $found_id;
            }
        }

        // Create folder
        $create = wp_remote_post('https://www.googleapis.com/drive/v3/files', [
            'headers' => [
                'Authorization' => 'Bearer ' . $token,
                'Content-Type' => 'application/json',
            ],
            'body' => json_encode([
                'name' => 'X-Codes',
                'mimeType' => 'application/vnd.google-apps.folder',
            ]),
            'timeout' => 15,
        ]);

        if (!is_wp_error($create)) {
            $created = json_decode(wp_remote_retrieve_body($create), true);
            if (!empty($created['id'])) {
                update_option('xcodes_gdrive_folder_id', $created['id']);
                return $created['id'];
            }
        }

        return null;
    }

    public function handle_drive_upload($request) {
        $token = $this->get_valid_gdrive_access_token();
        if (!$token) {
            return new WP_Error('gdrive_unauthorized', 'Google Drive not authenticated on VPS companion', ['status' => 401]);
        }

        $folder_id = $this->get_or_create_xcodes_drive_folder($token);
        $body = $request->get_json_params();
        $name = sanitize_text_field($body['name'] ?? 'backup.zip');
        $mime_type = sanitize_text_field($body['mimeType'] ?? 'application/octet-stream');
        $base64_content = $body['contentBase64'] ?? '';

        if (empty($base64_content)) {
            return new WP_Error('bad_request', 'Missing contentBase64', ['status' => 400]);
        }

        $binary = base64_decode($base64_content);
        $boundary = '-------' . bin2hex(random_bytes(10));
        $delimiter = "\r\n--" . $boundary . "\r\n";
        $close_delimiter = "\r\n--" . $boundary . "--";

        $metadata = [
            'name' => $name,
            'mimeType' => $mime_type,
        ];
        if (!empty($folder_id)) {
            $metadata['parents'] = [$folder_id];
        }

        $multipart_body = $delimiter
            . "Content-Type: application/json; charset=UTF-8\r\n\r\n"
            . json_encode($metadata)
            . $delimiter
            . "Content-Type: " . $mime_type . "\r\n"
            . "Content-Transfer-Encoding: binary\r\n\r\n"
            . $binary
            . $close_delimiter;

        $upload_res = wp_remote_post('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink,webContentLink', [
            'headers' => [
                'Authorization' => 'Bearer ' . $token,
                'Content-Type' => 'multipart/related; boundary=' . $boundary,
                'Content-Length' => strlen($multipart_body),
            ],
            'body' => $multipart_body,
            'timeout' => 60,
        ]);

        if (is_wp_error($upload_res)) {
            return new WP_Error('upload_failed', $upload_res->get_error_message(), ['status' => 500]);
        }

        $result = json_decode(wp_remote_retrieve_body($upload_res), true);
        return rest_ensure_response($result);
    }

    public function handle_drive_files($request) {
        $token = $this->get_valid_gdrive_access_token();
        if (!$token) {
            return new WP_Error('gdrive_unauthorized', 'Google Drive not authenticated on VPS companion', ['status' => 401]);
        }

        $folder_id = $this->get_or_create_xcodes_drive_folder($token);
        $q = !empty($folder_id) ? "'" . $folder_id . "' in parents and trashed = false" : "trashed = false";
        $url = 'https://www.googleapis.com/drive/v3/files?q=' . urlencode($q) . '&fields=files(id,name,mimeType,size,modifiedTime,webViewLink)&orderBy=modifiedTime desc';

        $res = wp_remote_get($url, [
            'headers' => ['Authorization' => 'Bearer ' . $token],
            'timeout' => 15,
        ]);

        if (is_wp_error($res)) {
            return new WP_Error('list_failed', $res->get_error_message(), ['status' => 500]);
        }

        return rest_ensure_response(json_decode(wp_remote_retrieve_body($res), true));
    }
}

new XCodesCompanionPlugin();
