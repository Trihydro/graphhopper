# GraphHopper Build Pipeline Documentation

## Overview

The GraphHopper build pipeline (`build.yml`) is a GitHub Actions workflow that automates building, testing, and deploying the GraphHopper routing engine to Azure DevOps. The pipeline runs on every push to the repository.

## Pipeline Structure

### Job 1: Build and Test
**Trigger:** Every push to any branch

This job builds and tests the GraphHopper project across multiple Java versions to ensure compatibility.

**Steps:**
1. **Checkout code** - Retrieves the repository code
2. **Setup Java** - Configures Java using the Temurin distribution
   - Tested versions: Java 25 and Java 26-ea (early access)
   - Uses a matrix strategy to test both versions in parallel
3. **Cache Maven artifacts** - Caches `~/.m2/repository` to speed up builds
4. **Cache node** - Caches `web-bundle/node` directory
5. **Cache node_modules** - Caches `web-bundle/node_modules` directory
6. **Build and Test** - Runs `mvn -B clean test`

### Job 2: Push to Azure DevOps
**Trigger:** Only runs on successful build when pushing to the `master` branch

This job packages the GraphHopper JAR file and pushes it to the Azure DevOps repository for deployment.

**Configuration Variables:**
- `AZURE_ORG`: trihydro
- `AZURE_PROJECT`: SDX
- `AZURE_REPO`: graphhopper
- `JAVA_VERSION`: 25

**Steps:**
1. **Checkout code** - Retrieves the repository code
2. **Package JAR** - Runs `mvn -B package -DskipTests` to create the JAR file
3. **Get JAR info** - Extracts JAR filename and creates a timestamp
4. **Clone Azure DevOps Repository** - Clones the Azure repo using PAT authentication
5. **Commit and push JAR** - Creates a new branch, backs up old JAR, commits new JAR
6. **Create Pull Request** - Creates PR in Azure DevOps with designated reviewers

## Required Secrets

The following GitHub repository secrets must be configured for the pipeline to work:

### `AZURE_DEVOPS_PAT`
**Type:** Personal Access Token  
**Purpose:** Authenticates GitHub Actions with Azure DevOps  
**Permissions Required:**
- Code: Read & Write

**How to Update:**
1. Go to Azure DevOps → User Settings → Personal Access Tokens
2. Create a new token or regenerate existing one with the required permissions
3. Copy the token value
4. Go to GitHub repository → Settings → Secrets and variables → Actions
5. Update or create `AZURE_DEVOPS_PAT` secret with the new token value

### `AZURE_GH_REPO_ID`
**Type:** Repository GUID  
**Purpose:** Identifies the Azure DevOps repository for API calls

**How to Find:**
1. Run command: `az repos list --organization "https://dev.azure.com/trihydro" --project "SDX" --query "[?name == 'graphhopper'].id" --output tsv`

### `TEAGHEN_DEVOPS_USER_ID` & `CHAN_DEVOPS_USER_ID`
**Type:** User GUID  
**Purpose:** Adds Teaghen and Chan as a PR reviewer automatically

**How to Find/Update:**
1. Make API call to: https://vssps.dev.azure.com/trihydro/_apis/graph/users?api-version=7.1-preview.1
   1. Authorization header: Basic `<AZURE_DEVOPS_PAT>`
   2. Content-Type header: application/json
2. Update the GitHub secret with the originId value.

## Maintenance Guide

### Updating Java Versions
To test against different Java versions:
1. Edit `.github/workflows/build.yml`
2. Modify the `matrix.java-version` array (currently `[25, 26-ea]`)
3. Update the `JAVA_VERSION` environment variable in the `push-to-azure` job if changing the deployment version

### Updating Azure DevOps Configuration
To change the target Azure DevOps organization, project, or repository:
1. Edit `.github/workflows/build.yml`
2. Update the environment variables in the `push-to-azure` job:
   - `AZURE_ORG`
   - `AZURE_PROJECT`
   - `AZURE_REPO`

### Adding/Removing Reviewers
To modify automatic PR reviewers:
1. Add the new reviewer user ID secrets in GitHub repository settings
2. Edit the `Create Pull Request in Azure DevOps` step in `build.yml`
3. Add/remove reviewer objects in the `reviewers` array of the PR payload

### Monitoring Secrets Expiration
**Recommended Schedule:**
- Azure DevOps PATs typically expire after 90 days depending on configuration
- Set up calendar reminders 2 weeks before known expiration dates

### Troubleshooting Common Issues

**Build fails but tests pass locally:**
- Check Java version compatibility (pipeline uses Java 25/26)
- Clear Maven cache by removing the cache action temporarily

**Azure push fails with an authentication error:**
- `AZURE_DEVOPS_PAT` has likely expired - regenerate it
- Verify the PAT has Code and Pull Request permissions

**Pull Request creation fails:**
- Verify `AZURE_GH_REPO_ID` is correct
- Check that reviewer IDs are valid and users have access to the repository
- Ensure the target branch (master) exists in Azure DevOps repo

## Workflow Diagram

```
┌─────────────────┐
│   Push Event    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│   Build & Test Job      │
│   - Java 25             │
│   - Java 26-ea          │
│   - Maven clean test    │
└────────┬────────────────┘
         │
         ▼
    [master branch?]
         │ Yes
         ▼
┌─────────────────────────┐
│  Push to Azure Job      │
│  1. Package JAR         │
│  2. Clone Azure repo    │
│  3. Create branch       │
│  4. Commit JAR          │
│  5. Push branch         │
│  6. Create PR           │
└─────────────────────────┘
```

