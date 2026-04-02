package cmd

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	log2 "log"

	"github.com/fatih/color"
	"github.com/google/uuid"
	"github.com/grokify/coreforge/identity/apikey"
	"github.com/spf13/cobra"
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/auth/scope"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/system"
)

func init() {
	rootCommand.AddCommand(createApiKeyCommand)
	rootCommand.AddCommand(listApiKeysCommand)

	rootCommand.AddCommand(getApiKeyCommand)
	getApiKeyCommand.Flags().StringVar(&getApiKeyID, "id", "", "API key ID (UUID, required)")
	err := getApiKeyCommand.MarkFlagRequired("id")
	if err != nil {
		return
	}

	rootCommand.AddCommand(revokeApiKeyCommand)
	revokeApiKeyCommand.Flags().StringVar(&revokeApiKeyID, "id", "", "API key ID (UUID, required)")
	revokeApiKeyCommand.Flags().StringVar(&revokeReason, "reason", "", "Optional reason for revocation")
	err = revokeApiKeyCommand.MarkFlagRequired("id")
	if err != nil {
		return
	}

	rootCommand.AddCommand(deleteApiKeyCommand)
	deleteApiKeyCommand.Flags().StringVar(&deleteApiKeyID, "id", "", "API key ID (UUID, required)")
	err = deleteApiKeyCommand.MarkFlagRequired("id")
	if err != nil {
		return
	}

}

// create-api-key flags (non-interactive mode)
var (
	createKeyName           string
	createKeyOwnerID        string
	createKeyOrgID          string
	createKeyScopes         string
	createKeyDescription    string
	createKeyEnvironment    string
	createKeyExpiresIn      string
	createKeyMetadata       []string
	createKeyNonInteractive bool
)

var createApiKeyCommand = &cobra.Command{
	Use:     "create-api-key",
	Short:   "Create a new API key",
	Aliases: []string{"ck", "create-key", "new-key"},
	Long:    "Create a new API key. Runs interactively by default; use --no-interactive with flags for non-interactive mode.",
	Run:     runCreateApiKey,
}

var listApiKeysCommand = &cobra.Command{
	Use:     "list-api-keys",
	Aliases: []string{"list-keys", "keys", "lk"},
	Short:   "List all API keys",
	Long:    "Fetches and displays all API keys from the database with basic information.",
	Run:     runListApiKeys,
}

var getApiKeyCommand = &cobra.Command{
	Use:     "show-api-key",
	Aliases: []string{"key-info", "show-key", "sk"},
	Short:   "Display detailed information about an API key by ID",
	Long:    "Fetches an API key by its UUID and displays all its details in a readable format.",
	Run:     runGetApiKey,
}

var (
	deleteApiKeyID string
	revokeApiKeyID string
	revokeReason   string
)

// Delete API key by ID
var deleteApiKeyCommand = &cobra.Command{
	Use:     "delete-api-key",
	Aliases: []string{"del-key", "remove-key", "delete-key", "dk"},
	Short:   "Delete an API key by ID",
	Long:    "Permanently deletes an API key from the database by its UUID.",
	Run:     runDeleteApiKey,
}

// Revoke API key by ID
var revokeApiKeyCommand = &cobra.Command{
	Use:     "revoke-api-key",
	Aliases: []string{"revoke-key", "rk"},
	Short:   "Revoke an API key by ID",
	Long:    "Marks an API key as revoked by its UUID, optionally providing a reason.",
	Run:     runRevokeApiKey,
}

func init() {
	createApiKeyCommand.Flags().StringVar(&createKeyName, "name", "", "Key name (required)")
	createApiKeyCommand.Flags().StringVar(&createKeyOwnerID, "owner-id", "", "Owner UUID (required)")
	createApiKeyCommand.Flags().StringVar(&createKeyScopes, "scopes", "", "Comma-separated scopes (e.g. app:admin,node)")
	createApiKeyCommand.Flags().StringVar(&createKeyDescription, "description", "", "Key description")
	createApiKeyCommand.Flags().StringVar(&createKeyEnvironment, "environment", "live", "Environment: live or test")
	createApiKeyCommand.Flags().StringVar(&createKeyExpiresIn, "expires-in", "", "Expiration duration (e.g. 24h, 168h, 720h)")
	createApiKeyCommand.Flags().StringSliceVar(&createKeyMetadata, "metadata", nil, "Metadata as key=value (can repeat)")
	createApiKeyCommand.Flags().BoolVar(&createKeyNonInteractive, "no-interactive", false, "Disable interactive prompts, use flags only")
}

func runDeleteApiKey(cmd *cobra.Command, args []string) {
	config.InitConfig()
	if err := database.Initialize(); err != nil {
		log2.Fatalf("Failed to initialize database: %v", err)
	}

	keyService := auth.NewKeyService()

	id, err := uuid.Parse(deleteApiKeyID)
	if err != nil {
		log2.Fatalf("Invalid UUID for --id: %v", err)
	}

	if err := keyService.Delete(cmd.Context(), id); err != nil {
		log2.Fatalf("Failed to delete API key: %v", err)
	}

	fmt.Printf("API key %s deleted successfully.\n", id)
}

func runRevokeApiKey(cmd *cobra.Command, args []string) {
	config.InitConfig()
	if err := database.Initialize(); err != nil {
		log2.Fatalf("Failed to initialize database: %v", err)
	}

	keyService := auth.NewKeyService()

	id, err := uuid.Parse(revokeApiKeyID)
	if err != nil {
		log2.Fatalf("Invalid UUID for --id: %v", err)
	}

	if err := keyService.Revoke(cmd.Context(), id, revokeReason); err != nil {
		log2.Fatalf("Failed to revoke API key: %v", err)
	}

	fmt.Printf("API key %s revoked successfully.\n", id)
	if revokeReason != "" {
		fmt.Printf("Reason: %s\n", revokeReason)
	}
}

var getApiKeyID string

func runGetApiKey(cmd *cobra.Command, args []string) {
	config.InitConfig()

	if err := database.Initialize(); err != nil {
		log2.Fatalf("Failed to initialize database: %v", err)
	}

	keyService := auth.NewKeyService()

	id, err := uuid.Parse(getApiKeyID)
	if err != nil {
		log2.Fatalf("Invalid UUID for --id: %v", err)
	}

	key, err := keyService.Get(cmd.Context(), id)
	if err != nil {
		log2.Fatalf("Failed to fetch API key: %v", err)
	}

	printAPIKeyDetails(key)
}

func printAPIKeyDetails(k *apikey.APIKey) {
	fmt.Println("\n=== API Key Details ===")
	fmt.Printf("ID:          %s\n", k.ID)
	fmt.Printf("Name:        %s\n", k.Name)
	fmt.Printf("Prefix:      %s\n", k.Prefix)
	fmt.Printf("Owner ID:    %s\n", k.OwnerID)
	fmt.Printf("Environment: %s\n", k.Environment)
	fmt.Printf("Scopes:      %s\n", strings.Join(k.Scopes, ", "))
	fmt.Printf("Description: %s\n", k.Description)
	fmt.Printf("Revoked:     %t\n", k.Revoked)
	if k.Revoked && k.RevokedAt != nil {
		fmt.Printf("Revoked At:  %s\n", k.RevokedAt.Format(time.RFC3339))
		fmt.Printf("Reason:      %s\n", k.RevokedReason)
	}
	if k.ExpiresAt != nil {
		fmt.Printf("Expires At:  %s\n", k.ExpiresAt.Format(time.RFC3339))
	}
	if k.LastUsedAt != nil {
		fmt.Printf("Last Used:   %s\n", k.LastUsedAt.Format(time.RFC3339))
	}
	if k.LastUsedIP != "" {
		fmt.Printf("Last Used IP:%s\n", k.LastUsedIP)
	}
	if len(k.Metadata) > 0 {
		fmt.Println("Metadata:")
		for kName, v := range k.Metadata {
			fmt.Printf("  %s: %s\n", kName, v)
		}
	}
	fmt.Printf("Created At:  %s\n", k.CreatedAt.Format(time.RFC3339))
	fmt.Printf("Updated At:  %s\n", k.UpdatedAt.Format(time.RFC3339))
	fmt.Println("======================")
}

func runListApiKeys(cmd *cobra.Command, args []string) {
	config.InitConfig()

	if err := database.Initialize(); err != nil {
		log2.Fatalf("Failed to initialize database: %v", err)
	}

	keyService := auth.NewKeyService()
	keys, err := keyService.ListByOrganization(cmd.Context(), uuid.Nil)
	if err != nil {
		log2.Fatalf("Failed to fetch API keys: %v", err)
	}

	if len(keys) == 0 {
		fmt.Println("No API keys found.")
		return
	}

	// Calculate dynamic column widths
	idWidth := len("ID")
	nameWidth := len("Name")
	prefixWidth := len("Prefix")
	scopesWidth := len("Scopes")
	expiresWidth := len("Expires At")

	for _, k := range keys {
		if len(k.ID.String()) > idWidth {
			idWidth = len(k.ID.String())
		}

		name := k.Name
		if len(name) > 36 {
			name = name[:34] + ".."
		}
		if len(name) > nameWidth {
			nameWidth = len(name)
		}

		if len(k.Prefix) > prefixWidth {
			prefixWidth = len(k.Prefix)
		}

		scopeStr := strings.Join(k.Scopes, ",")
		if len(scopeStr) > scopesWidth {
			scopesWidth = len(scopeStr)
		}

		expStr := "never"
		if k.ExpiresAt != nil {
			expStr = k.ExpiresAt.Format("2006-01-02 15:04")
		}
		if len(expStr) > expiresWidth {
			expiresWidth = len(expStr)
		}
	}

	sep := "   " // 3 spaces

	// Header
	fmt.Println("=== API Keys ===")
	fmt.Printf("%-*s%s%-*s%s%-*s%s%-*s%s%-*s\n",
		idWidth, "ID",
		sep,
		nameWidth, "Name",
		sep,
		prefixWidth, "Prefix",
		sep,
		scopesWidth, "Scopes",
		sep,
		expiresWidth, "Expires At",
	)

	totalWidth := idWidth + len(sep) + nameWidth + len(sep) + prefixWidth + len(sep) +
		scopesWidth + len(sep) + expiresWidth

	fmt.Println(strings.Repeat("-", totalWidth))

	now := time.Now()

	// Print rows
	for _, k := range keys {
		name := k.Name
		if len(name) > 36 {
			name = name[:34] + ".."
		}

		scopeStr := strings.Join(k.Scopes, ",")

		expStr := "never"
		expired := false
		if k.ExpiresAt != nil {
			expStr = k.ExpiresAt.Format("2006-01-02 15:04")
			if now.After(*k.ExpiresAt) {
				var Gray = color.New(color.FgHiBlack).SprintFunc()
				expStr = Gray(expStr)
				expired = true
			}
		}

		line := fmt.Sprintf("%-*s%s%-*s%s%-*s%s%-*s%s%-*s",
			idWidth, k.ID,
			sep,
			nameWidth, name,
			sep,
			prefixWidth, k.Prefix,
			sep,
			scopesWidth, scopeStr,
			sep,
			expiresWidth, expStr,
		)

		if k.Revoked {
			line += " " + system.Red("(revoked)")
		}
		if expired {
			line += " " + system.Red("(expired)")
		}

		fmt.Println(line)
	}

	fmt.Println(strings.Repeat("-", totalWidth))
}

func runCreateApiKey(cmd *cobra.Command, args []string) {
	config.InitConfig()

	if err := database.Initialize(); err != nil {
		log2.Fatalf("Failed to initialize database: %v", err)
	}

	keyService := auth.NewKeyService()

	var req apikey.CreateKeyRequest
	if createKeyNonInteractive {
		var err error
		req, err = buildCreateKeyRequestFromFlags()
		if err != nil {
			log2.Fatalf("Invalid flags: %v", err)
		}
	} else {
		var err error
		req, err = buildCreateKeyRequestInteractive()
		if err != nil {
			log2.Fatalf("Failed to build request: %v", err)
		}
	}

	generated, err := keyService.Create(cmd.Context(), req)
	if err != nil {
		log2.Fatalf("Failed to create API key: %v", err)
	}

	fmt.Println("\n=== API Key Created ===")
	fmt.Printf("Key: %s\n", system.Blue(generated.Key))
	fmt.Printf("ID: %s\n", generated.APIKey.ID)
	fmt.Printf("Name: %s\n", generated.APIKey.Name)
	fmt.Printf("Prefix: %s\n", generated.APIKey.Prefix)
	fmt.Println("======================")
}

func buildCreateKeyRequestFromFlags() (apikey.CreateKeyRequest, error) {
	var req apikey.CreateKeyRequest

	if createKeyName == "" {
		return req, fmt.Errorf("--name is required in non-interactive mode")
	}
	req.Name = createKeyName

	if createKeyOwnerID == "" {
		return req, fmt.Errorf("--owner-id is required in non-interactive mode")
	}
	ownerID, err := uuid.Parse(createKeyOwnerID)
	if err != nil {
		return req, fmt.Errorf("invalid --owner-id: %w", err)
	}
	req.OwnerID = ownerID

	req.OrganizationID = &uuid.Nil

	if createKeyScopes != "" {
		req.Scopes = parseScopes(createKeyScopes)
	}

	req.Description = createKeyDescription

	env, err := apikey.ParseEnvironment(createKeyEnvironment)
	if err != nil {
		return req, fmt.Errorf("invalid --environment: %w", err)
	}
	req.Environment = env

	if createKeyExpiresIn != "" {
		d, err := time.ParseDuration(createKeyExpiresIn)
		if err != nil {
			return req, fmt.Errorf("invalid --expires-in: %w", err)
		}
		req.ExpiresIn = &d
	}

	if len(createKeyMetadata) > 0 {
		req.Metadata = make(map[string]string)
		for _, kv := range createKeyMetadata {
			k, v, ok := strings.Cut(kv, "=")
			if !ok {
				return req, fmt.Errorf("invalid metadata %q: expected key=value", kv)
			}
			req.Metadata[strings.TrimSpace(k)] = strings.TrimSpace(v)
		}
	}

	return req, nil
}

func buildCreateKeyRequestInteractive() (apikey.CreateKeyRequest, error) {
	reader := bufio.NewReader(os.Stdin)
	prompt := func(label, defaultVal string) string {
		if defaultVal != "" {
			fmt.Printf("%s [%s]: ", label, defaultVal)
		} else {
			fmt.Printf("%s: ", label)
		}
		line, _ := reader.ReadString('\n')
		line = strings.TrimSpace(line)
		if line == "" && defaultVal != "" {
			return defaultVal
		}
		return line
	}

	var req apikey.CreateKeyRequest

	fmt.Println("\n--- Create API Key ---")
	req.Name = prompt("Name", createKeyName)
	if req.Name == "" {
		return req, fmt.Errorf("name is required")
	}

	req.OwnerID = uuid.New()

	keyTypeStr := prompt("Key type (n=node, a=application)", "")
	keyTypeStr = strings.ToLower(strings.TrimSpace(keyTypeStr))
	var baseScopes []string
	switch keyTypeStr {
	case "n", "node":
		baseScopes = []string{scope.Node}
	case "a", "app", "application":
		baseScopes = []string{scope.AppAdmin}
	default:
		return req, fmt.Errorf("invalid key type: use n for node or a for application")
	}

	additionalStr := prompt("Additional scopes (comma-separated, or empty for none)", "")
	additional := parseScopes(additionalStr)
	req.Scopes = append(baseScopes, additional...)

	req.Description = prompt("Description", createKeyDescription)
	req.Environment = apikey.EnvLive
	req.OrganizationID = &uuid.Nil

	expStr := prompt("Expires in (e.g. 24h, 7d, 30d, or empty for never)", createKeyExpiresIn)
	if expStr != "" {
		d, err := parseExpiresIn(expStr)
		if err != nil {
			return req, fmt.Errorf("invalid duration: %w", err)
		}
		req.ExpiresIn = &d
	}

	return req, nil
}

// parseExpiresIn parses duration strings like "24h", "7d", "30d".
func parseExpiresIn(s string) (time.Duration, error) {
	s = strings.TrimSpace(s)
	if strings.HasSuffix(s, "d") {
		n, err := strconv.Atoi(strings.TrimSuffix(s, "d"))
		if err != nil {
			return 0, fmt.Errorf("invalid days: %w", err)
		}
		return time.Duration(n) * 24 * time.Hour, nil
	}
	return time.ParseDuration(s)
}

func parseScopes(s string) []string {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
