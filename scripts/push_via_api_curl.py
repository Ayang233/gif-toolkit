import base64
import json
import os
import subprocess
import tempfile
import time

# 仅用于「git push 走不通、但 api.github.com 可达」的受限网络（见 AGENTS.md 构建环境的两个坑 4）。
# 前置：本机 git 凭据管理器里已有一把能写这个仓库的 token（脚本从 credential.helper 取，不读环境变量）。
# 仓库根目录按脚本位置推导，不写死本机路径。
REPO = "Ayang233/gif-toolkit"
CWD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

cred = subprocess.run(
    ["git", "-c", "credential.https://github.com.helper=", "-c", "credential.helper=manager",
     "credential", "fill"],
    input="protocol=https\nhost=github.com\n\n", capture_output=True, text=True, cwd=CWD,
)
TOKEN = dict(l.split("=", 1) for l in cred.stdout.strip().splitlines() if "=" in l)["password"]


def api(method, url, payload=None, binary=None, content_type="application/json", timeout=120):
    """用 curl 发请求（本机 Python 的 TLS 栈连 GitHub 会 SSL EOF，curl 正常）。"""
    with tempfile.NamedTemporaryFile("wb", delete=False) as f:
        hdr = f.name
        f.write(f'header = "Authorization: Bearer {TOKEN}"\n'.encode())
        f.write(b'header = "Accept: application/vnd.github+json"\n')
        f.write(b'header = "User-Agent: wb"\n')
        if binary is not None or payload is not None:
            f.write(f'header = "Content-Type: {content_type}"\n'.encode())
    try:
        cmd = ["curl", "-sS", "--max-time", str(timeout), "-K", hdr, "-X", method, url]
        data = None
        if binary is not None:
            data = binary
        elif payload is not None:
            data = json.dumps(payload).encode()
        if data is not None:
            cmd += ["--data-binary", "@-"]
        last = None
        for attempt in range(5):
            r = subprocess.run(cmd, input=data, capture_output=True, cwd=CWD)
            if r.returncode == 0:
                out = r.stdout.decode()
                return json.loads(out) if out.strip() else {}
            last = RuntimeError(r.stderr.decode()[:200])
            time.sleep(2 * (attempt + 1))
        raise last
    finally:
        os.unlink(hdr)


def ref_sha(ref):
    return api("GET", f"https://api.github.com/repos/{REPO}/git/ref/{ref}")["object"]["sha"]


remote = ref_sha("heads/main")
print("remote head:", remote[:12])
base_tree = api("GET", f"https://api.github.com/repos/{REPO}/git/commits/{remote}")["tree"]["sha"]

files = subprocess.run(["git", "diff", "--name-only", "HEAD~1", "HEAD"],
                       capture_output=True, text=True, cwd=CWD).stdout.split()
print("changed:", files)

tree = []
for f in files:
    blob = subprocess.run(["git", "show", f"HEAD:{f}"], capture_output=True, cwd=CWD).stdout
    sha = api("POST", f"https://api.github.com/repos/{REPO}/git/blobs",
              {"content": base64.b64encode(blob).decode(), "encoding": "base64"})["sha"]
    tree.append({"path": f, "mode": "100644", "type": "blob", "sha": sha})

new_tree = api("POST", f"https://api.github.com/repos/{REPO}/git/trees",
               {"base_tree": base_tree, "tree": tree})["sha"]
msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], capture_output=True, text=True, cwd=CWD).stdout.strip()
commit = api("POST", f"https://api.github.com/repos/{REPO}/git/commits",
             {"message": msg, "tree": new_tree, "parents": [remote]})["sha"]
api("PATCH", f"https://api.github.com/repos/{REPO}/git/refs/heads/main", {"sha": commit, "force": True})
print("main ->", commit[:12])

tag = api("POST", f"https://api.github.com/repos/{REPO}/git/tags",
          {"tag": "v1.12", "message": "GIF 助手 1.12：MIUIX 基线 f3829d3d（0.9.5-beta02）",
           "object": commit, "type": "commit"})["sha"]
api("PATCH", f"https://api.github.com/repos/{REPO}/git/refs/tags/v1.12", {"sha": tag, "force": True})
print("tag v1.12 updated")

rel = [r for r in api("GET", f"https://api.github.com/repos/{REPO}/releases?per_page=5") if r["draft"]][0]
for a in rel["assets"]:
    api("DELETE", f"https://api.github.com/repos/{REPO}/releases/assets/{a['id']}")
    print("deleted asset", a["name"])

apk = open(os.path.join(CWD, "build/outputs/apk/release/GifHelper-release.apk"), "rb").read()
asset = api("POST",
            f"https://uploads.github.com/repos/{REPO}/releases/{rel['id']}/assets?name=GifHelper-1.12-release.apk",
            binary=apk, content_type="application/vnd.android.package-archive", timeout=300)
print("asset:", asset["size"], "state:", asset["state"])

body = open(os.path.join(CWD, "docs/release-notes-1.12.md"), encoding="utf-8").read()
api("PATCH", f"https://api.github.com/repos/{REPO}/releases/{rel['id']}", {"body": body})
print("release body updated")
