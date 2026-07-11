import subprocess
import sys
import time

dependencies = [
    "fastapi",
    "uvicorn[standard]",
    "websockets",
    "pydantic",
    "pydantic-settings",
    "python-dotenv",
    "google-genai"
]

def install_with_retry(package: str, retries: int = 4) -> bool:
    cmd = [
        sys.executable, "-m",
        "pip", "install", package,
        "--trusted-host", "pypi.org",
        "--trusted-host", "files.pythonhosted.org",
        "--trusted-host", "pypi.python.org",
        "--no-cache-dir"
    ]
    
    for attempt in range(1, retries + 1):
        print(f"Attempt {attempt}/{retries}: Installing {package}...")
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode == 0:
            print(f"Successfully installed {package}!\n")
            return True
        else:
            print(f"Failed to install {package} on attempt {attempt}.")
            print(f"Error details:\n{result.stderr or result.stdout}\n")
            if attempt < retries:
                print("Waiting 3 seconds before retrying...")
                time.sleep(3)
                
    return False

def main():
    print("Starting sequential dependency installation with auto-retry...")
    failed = []
    for pkg in dependencies:
        success = install_with_retry(pkg)
        if not success:
            failed.append(pkg)
            
    if failed:
        print(f"Installation completed with failures. The following packages failed: {failed}")
        sys.exit(1)
    else:
        print("All dependencies successfully installed!")
        sys.exit(0)

if __name__ == "__main__":
    main()
