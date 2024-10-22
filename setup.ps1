# Define the server directory and the client workspaces
$server_dir = "C:/Users/uhfon/OneDrive/Documents/javardair/server"
$workspaces = @(
    "C:/Users/uhfon/OneDrive/Documents/javardair/workspace_client1",
    "C:/Users/uhfon/OneDrive/Documents/javardair/workspace_client2",
    "C:/Users/uhfon/OneDrive/Documents/javardair/workspace_client3"
)

# Loop over all Java files in the server directory
Get-ChildItem "$server_dir" -Filter *.java | ForEach-Object {
    $server_file = $_.FullName
    $filename = $_.Name

    # Loop over each workspace
    foreach ($workspace in $workspaces) {
        # Recursively search for Java files in all subdirectories of the workspace
        Get-ChildItem -Path $workspace -Recurse -Filter *.java | ForEach-Object {
            $target_file = $_.FullName

            # Check if the current file has the same name as the one in the server
            if ($_.Name -eq $filename) {
                # Copy the server file content to the target file in the workspace
                Copy-Item -Force $server_file $target_file
                Write-Host "Replaced $target_file with $server_file content"
            }
        }
    }
}
