package digital.vmstudio.code.core.ssh.recipes

/**
 * The catalog of recipes the app can provision on a server.
 *
 * Each recipe is a scripted, repeatable setup. The WordPress sandbox recipe is
 * ported from the old Flutter app's `WpDevService`, reframed as a generic recipe
 * so the same executor can drive other stacks (Node.js, Docker, etc.) without
 * duplicating the approval-gate plumbing.
 *
 * Recipes are stateless definitions — they describe *what* to run, not the live
 * state of an installation. Status checks go through [RecipeExecutor.checkStatus].
 */
object RecipeRegistry {

    /** WordPress sandbox with SQLite — zero MySQL setup, runs on a configurable port. */
    val wordpress = Recipe(
        id = "wordpress-sandbox",
        name = "WordPress Sandbox",
        description = "A full WordPress development environment using SQLite " +
            "(no MySQL required). Includes WP-CLI and runs on a configurable port.",
        estimatedSeconds = 180,
        statusProbe = "test -f ~/apps/wordpress-sandbox/wp-config.php && echo installed",
        steps = listOf(
            RecipeStep(
                progressWeight = 0.10,
                label = "Verifying PHP and tools…",
                command = "which php || echo missing",
            ),
            RecipeStep(
                progressWeight = 0.15,
                label = "Installing PHP CLI…",
                command = "sudo apt-get update -y && sudo apt-get install -y " +
                    "php-cli php-sqlite3 php-curl php-zip php-xml php-mbstring",
            ),
            RecipeStep(
                progressWeight = 0.15,
                label = "Downloading WP-CLI…",
                command = "curl -s -O https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar " +
                    "&& chmod +x wp-cli.phar " +
                    "&& sudo mv wp-cli.phar /usr/local/bin/wp 2>/dev/null || true",
            ),
            RecipeStep(
                progressWeight = 0.10,
                label = "Creating workspace…",
                command = "mkdir -p ~/apps/wordpress-sandbox",
            ),
            RecipeStep(
                progressWeight = 0.15,
                label = "Downloading WordPress core…",
                command = "cd ~/apps/wordpress-sandbox && " +
                    "(wp core download --allow-root 2>/dev/null || " +
                    "(curl -s https://wordpress.org/latest.tar.gz | tar -xz --strip-components=1))",
            ),
            RecipeStep(
                progressWeight = 0.15,
                label = "Configuring SQLite database…",
                command = """
cd ~/apps/wordpress-sandbox
wp config create --dbname=wordpress --dbuser=root --dbpass=root --allow-root --skip-check --force 2>/dev/null || true
mkdir -p wp-content/plugins/sqlite-database-integration
curl -s -L https://downloads.wordpress.org/plugin/sqlite-database-integration.latest-stable.zip -o /tmp/sqlite.zip
unzip -q -o /tmp/sqlite.zip -d wp-content/plugins/ 2>/dev/null || true
cp wp-content/plugins/sqlite-database-integration/db.copy wp-content/db.php 2>/dev/null || true
                """.trimIndent(),
            ),
            RecipeStep(
                progressWeight = 0.10,
                label = "Finalizing installation…",
                command = "cd ~/apps/wordpress-sandbox && " +
                    "wp core install --url=http://localhost:8080 " +
                    "--title=\"VMStudio Dev Sandbox\" " +
                    "--admin_user=admin --admin_password=admin " +
                    "--admin_email=admin@vmstudio.local " +
                    "--allow-root --skip-email 2>/dev/null || true",
            ),
            RecipeStep(
                progressWeight = 0.10,
                label = "Starting server on port 8080…",
                command = "pm2 start \"php -S 0.0.0.0:8080 -t ~/apps/wordpress-sandbox\" " +
                    "--name \"wp-sandbox\" 2>/dev/null || " +
                    "(nohup php -S 0.0.0.0:8080 -t ~/apps/wordpress-sandbox > /tmp/wp.log 2>&1 &)",
            ),
        ),
    )

    /** All available recipes, in display order. */
    val all: List<Recipe> = listOf(wordpress)

    fun byId(id: String): Recipe? = all.firstOrNull { it.id == id }
}
