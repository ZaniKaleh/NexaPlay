"""Parse and sanity-check the GitHub Actions workflow before building."""

from pathlib import Path
try:
    import yaml
except ImportError as exc:  # pragma: no cover - used only in CI setup
    raise SystemExit("PyYAML is required: python -m pip install PyYAML") from exc

workflow_path = Path(__file__).parents[1] / ".github" / "workflows" / "android.yml"
if not workflow_path.is_file():
    raise SystemExit(
        "Workflow file not found: .github/workflows/android.yml. "
        "Create this exact path in the repository root; do not upload android.yml to the root."
    )
try:
    data = yaml.load(workflow_path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
except yaml.YAMLError as exc:
    raise SystemExit(f"Invalid YAML: {exc}") from exc

if not isinstance(data, dict):
    raise SystemExit("Workflow root must be a YAML mapping")

triggers = data.get("on")
if not isinstance(triggers, dict) or "workflow_dispatch" not in triggers:
    raise SystemExit("Workflow must have a workflow_dispatch trigger")
if "push" in triggers or "pull_request" in triggers:
    raise SystemExit("This project is configured for manual builds only")

jobs = data.get("jobs")
job = jobs.get("build-debug-apk") if isinstance(jobs, dict) else None
if not isinstance(job, dict):
    raise SystemExit("Missing jobs.build-debug-apk")

steps = job.get("steps")
if not isinstance(steps, list):
    raise SystemExit("Build job must contain a steps list")

joined_steps = "\n".join(
    " ".join(str(value) for value in step.values())
    for step in steps
    if isinstance(step, dict)
)
required_markers = (
    "actions/checkout@",
    "actions/setup-java@",
    "actions/setup-python@",
    "android-actions/setup-android@",
    "gradle/actions/setup-gradle@",
    "unittest discover",
    ":app:assembleDebug",
    "actions/upload-artifact@",
)
missing = [marker for marker in required_markers if marker not in joined_steps]
if missing:
    raise SystemExit("Workflow is missing required steps: " + ", ".join(missing))

test_file = Path(__file__).parents[1] / "app" / "src" / "test" / "python" / "test_engine.py"
if not test_file.is_file():
    raise SystemExit("Python unit test file is missing")

print("Workflow YAML syntax and structure OK")
