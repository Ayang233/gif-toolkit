import base64
import json
import os
import subprocess
import tempfile
import time

# 仅用于「git push 走不通、但 api.github.com 可达」的受限网络（见 AGENTS.md 构建环境的两个坑 4）。
# 前置：本机 git 凭据管理器里已有一把能写这个仓库的 token（脚本从 credential.helper 取，不读环境变量）。
# 仓库根目录按脚本位置推导，不写死本机路径。
#
# 只推代码：
#   python scripts/push_via_api_curl.py
# 推代码 + 打 tag + 传 APK + 写发布说明：
#   MOVE_TAG=v1.13 RELEASE_NOTES=docs/release-notes-1.13.md PUBLISH=1 python scripts/push_via_api_curl.py
#
# 环境变量：
#   MOVE_TAG      要指向本次提交的 tag，没有就新建、有就移动
#   RELEASE_NOTES 发布说明 md 路径，不设就跳过 Release 那一段
#   ASSET_NAME    Release 里的文件名，默认按 tag 拼成 GifToolkit-<版本>-release.apk
#   PUBLISH=1     资产和正文写好后把草稿 Release 正式发布
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

# name-status 的首字母就够区分增删改（改名会被 git 报成 D + A）。
status = subprocess.run(["git", "diff", "--name-status", "HEAD~1", "HEAD"],
                        capture_output=True, text=True, cwd=CWD).stdout.split("\n")
entries = [l.split("\t") for l in status if l.strip()]
print("changed:", [(e[0], e[-1]) for e in entries])

tree = []
for entry in entries:
    kind, path = entry[0], entry[-1]
    if kind == "D":
        # 删文件在 tree API 里就是 sha = null，漏掉这条远端会留着旧文件
        tree.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        continue
    blob = subprocess.run(["git", "show", f"HEAD:{path}"], capture_output=True, cwd=CWD).stdout
    sha = api("POST", f"https://api.github.com/repos/{REPO}/git/blobs",
              {"content": base64.b64encode(blob).decode(), "encoding": "base64"})["sha"]
    # 可执行位别丢（gradlew、*.sh）
    meta = subprocess.run(["git", "ls-tree", "HEAD", "--", path],
                          capture_output=True, text=True, cwd=CWD).stdout.split()
    tree.append({"path": path, "mode": meta[0] if meta else "100644",
                 "type": "blob", "sha": sha})

new_tree = api("POST", f"https://api.github.com/repos/{REPO}/git/trees",
               {"base_tree": base_tree, "tree": tree})["sha"]
msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], capture_output=True, text=True, cwd=CWD).stdout.strip()
commit = api("POST", f"https://api.github.com/repos/{REPO}/git/commits",
             {"message": msg, "tree": new_tree, "parents": [remote]})["sha"]
api("PATCH", f"https://api.github.com/repos/{REPO}/git/refs/heads/main", {"sha": commit, "force": True})
print("main ->", commit[:12])

# 只有要发版时才动 tag：发过版之后再推文档/脚本，不该把 tag 从已发布的提交上挪走。
tag_name = os.environ.get("MOVE_TAG")
if tag_name:
    tag_obj = api("POST", f"https://api.github.com/repos/{REPO}/git/tags",
                  {"tag": tag_name, "message": "GifToolkit " + tag_name.lstrip("v"),
                   "object": commit, "type": "commit"})["sha"]
    # 404 时响应体里没有 object 字段，直接看这个比解析状态码省事
    exists = "object" in api("GET", f"https://api.github.com/repos/{REPO}/git/ref/tags/{tag_name}")
    if not exists:
        api("POST", f"https://api.github.com/repos/{REPO}/git/refs",
            {"ref": f"refs/tags/{tag_name}", "sha": tag_obj})
        print("tag", tag_name, "created ->", commit[:12])
    else:
        api("PATCH", f"https://api.github.com/repos/{REPO}/git/refs/tags/{tag_name}",
            {"sha": tag_obj, "force": True})
        print("tag", tag_name, "moved ->", commit[:12])
else:
    print("未设置 MOVE_TAG，tag 保持不动")

notes_path = os.environ.get("RELEASE_NOTES")
if not notes_path:
    print("未设置 RELEASE_NOTES，跳过 Release 资产与正文")
    raise SystemExit(0)

body = open(os.path.join(CWD, notes_path), encoding="utf-8").read()
version = (tag_name or "").lstrip("v") or "release"
asset_name = os.environ.get("ASSET_NAME") or f"GifToolkit-{version}-release.apk"

# 优先找这个 tag 的草稿；没有就建一个，省得每次都去网页点「新建草稿」。
data = api("GET", f"https://api.github.com/repos/{REPO}/releases?per_page=20")
rel = next((r for r in data if r["draft"] and r["tag_name"] == (tag_name or "")), None)
if rel is None:
    rel = api("POST", f"https://api.github.com/repos/{REPO}/releases",
              {"tag_name": tag_name, "name": "GifToolkit " + version,
               "body": body, "draft": True, "prerelease": False})
    print("draft release created:", rel["id"])

for a in rel["assets"]:
    api("DELETE", f"https://api.github.com/repos/{REPO}/releases/assets/{a['id']}")
    print("deleted asset", a["name"])

apk = open(os.path.join(CWD, "build/outputs/apk/release/GifToolkit-release.apk"), "rb").read()
asset = api("POST",
            f"https://uploads.github.com/repos/{REPO}/releases/{rel['id']}/assets?name={asset_name}",
            binary=apk, content_type="application/vnd.android.package-archive", timeout=600)
print("asset:", asset["name"], asset["size"], "state:", asset["state"])

payload = {"body": body}
if os.environ.get("PUBLISH") == "1":
    payload["draft"] = False
rel = api("PATCH", f"https://api.github.com/repos/{REPO}/releases/{rel['id']}", payload)
print("release:", rel["tag_name"], "draft=" + str(rel["draft"]), rel["html_url"])
