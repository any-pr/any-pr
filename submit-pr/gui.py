# -*- coding: utf-8 -*-
"""
gui.py — submit_pr 的图形界面（纯标准库 tkinter，无第三方依赖）。

用法: python submit-pr/submit_pr.py --gui   或   pythonw submit-pr/gui.py
"""

from __future__ import annotations

import queue
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext, ttk

from runner import run_plan


class GuiReporter:
    """把 worker 线程里的进度事件转发到 UI 线程（经队列）。"""

    def __init__(self, q: queue.Queue):
        self.q = q

    def __call__(self, ev: str, **kw) -> None:
        self.q.put((ev, kw))


class App:
    def __init__(self, root: tk.Tk):
        self.root = root
        root.title("submit-pr — any-pr 自动提交（自动拆分，保证合并）")
        root.geometry("880x680")
        self.q: queue.Queue = queue.Queue()
        self.running = False
        self._build_settings()
        self._build_progress()
        self._build_buttons()
        root.after(120, self._poll)

    # ------------------------------------------------------------------ 设置
    def _build_settings(self):
        box = ttk.LabelFrame(self.root, text="提交设置")
        box.pack(fill="x", padx=8, pady=(8, 4))
        self.mode = tk.StringVar(value="submit")
        row = ttk.Frame(box)
        row.pack(fill="x", padx=6, pady=3)
        ttk.Radiobutton(row, text="提交文件", variable=self.mode, value="submit",
                        command=self._toggle_mode).pack(side="left")
        ttk.Radiobutton(row, text="删除仓库文件", variable=self.mode,
                        value="delete",
                        command=self._toggle_mode).pack(side="left", padx=10)

        self.src_area = ttk.Frame(box)
        self.src_area.pack(fill="x", padx=6, pady=2)
        self.src_var = tk.StringVar()
        ttk.Label(self.src_area, text="源文件/目录（多个用 | 分隔）").pack(anchor="w")
        erow = ttk.Frame(self.src_area)
        erow.pack(fill="x")
        ttk.Entry(erow, textvariable=self.src_var).pack(side="left", fill="x",
                                                        expand=True)
        ttk.Button(erow, text="添加文件", width=9,
                   command=self._add_files).pack(side="left", padx=3)
        ttk.Button(erow, text="添加目录", width=9,
                   command=self._add_dir).pack(side="left")

        self.del_area = ttk.Frame(box)
        ttk.Label(self.del_area,
                  text="要删除的仓库路径（每行一个，相对仓库根）").pack(anchor="w")
        self.del_text = tk.Text(self.del_area, height=4)
        self.del_text.pack(fill="x")

        row2 = ttk.Frame(box)
        row2.pack(fill="x", padx=6, pady=3)
        ttk.Label(row2, text="仓库内目标目录").pack(side="left")
        self.dest = tk.StringVar()
        ttk.Entry(row2, width=22, textvariable=self.dest).pack(side="left",
                                                               padx=(4, 12))
        ttk.Label(row2, text="PR 标题（可选）").pack(side="left")
        self.title = tk.StringVar()
        ttk.Entry(row2, width=30, textvariable=self.title).pack(side="left",
                                                                padx=4)
        row3 = ttk.Frame(box)
        row3.pack(fill="x", padx=6, pady=3)
        self.dry = tk.BooleanVar(value=False)
        ttk.Checkbutton(row3, text="仅预演（dry-run）",
                        variable=self.dry).pack(side="left")
        ttk.Label(row3, text="并发数（0=自动）").pack(side="left", padx=(14, 4))
        self.workers = tk.StringVar(value="0")
        ttk.Spinbox(row3, from_=0, to=10, width=4,
                    textvariable=self.workers).pack(side="left")

    def _add_files(self):
        for p in filedialog.askopenfilenames():
            self._append_src(p)

    def _add_dir(self):
        d = filedialog.askdirectory()
        if d:
            self._append_src(d)

    def _append_src(self, p: str):
        cur = [s.strip() for s in self.src_var.get().split("|") if s.strip()]
        if p not in cur:
            cur.append(p)
        self.src_var.set(" | ".join(cur))

    def _toggle_mode(self):
        if self.mode.get() == "submit":
            self.src_area.pack(fill="x", padx=6, pady=2)
            self.del_area.pack_forget()
        else:
            self.del_area.pack(fill="x", padx=6, pady=2)
            self.src_area.pack_forget()

    # ------------------------------------------------------------------ 进度
    def _build_progress(self):
        box = ttk.LabelFrame(self.root, text="进度")
        box.pack(fill="both", expand=True, padx=8, pady=4)
        prow = ttk.Frame(box)
        prow.pack(fill="x", padx=6, pady=(4, 0))
        self.bar = ttk.Progressbar(prow, mode="determinate")
        self.bar.pack(side="left", fill="x", expand=True)
        self.tally = tk.StringVar(value="就绪")
        ttk.Label(prow, textvariable=self.tally, width=30).pack(side="left",
                                                                padx=8)
        cols = ("task", "progress", "status")
        self.tree = ttk.Treeview(box, columns=cols, show="headings", height=7)
        for c, w, t in (("task", 340, "任务"), ("progress", 120, "步骤"),
                        ("status", 120, "状态")):
            self.tree.heading(c, text=t)
            self.tree.column(c, width=w)
        self.tree.tag_configure("ok", foreground="green")
        self.tree.tag_configure("bad", foreground="red")
        self.tree.tag_configure("warn", foreground="#b8860b")
        self.tree.pack(fill="x", padx=6, pady=4)
        self.log = scrolledtext.ScrolledText(box, height=10, state="disabled")
        self.log.pack(fill="both", expand=True, padx=6, pady=(0, 6))

    def _log(self, text: str):
        self.log.configure(state="normal")
        self.log.insert("end", text + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    # ------------------------------------------------------------------ 按钮
    def _build_buttons(self):
        row = ttk.Frame(self.root)
        row.pack(fill="x", padx=8, pady=(2, 8))
        self.btn_dry = ttk.Button(row, text="预  演", command=lambda:
                                  self._start(dry=True))
        self.btn_dry.pack(side="left")
        self.btn_go = ttk.Button(row, text="提  交", command=lambda:
                                 self._start(dry=False))
        self.btn_go.pack(side="left", padx=8)
        self.status = tk.StringVar(value="就绪")
        ttk.Label(row, textvariable=self.status).pack(side="left", padx=12)

    def _start(self, dry: bool):
        if self.running:
            return
        try:
            opts = self._collect_opts(dry)
        except ValueError as e:
            messagebox.showwarning("参数不完整", str(e))
            return
        self.running = True
        self.btn_dry.configure(state="disabled")
        self.btn_go.configure(state="disabled")
        self.status.set("运行中…")
        self.tree.delete(*self.tree.get_children())
        self.bar.configure(value=0, maximum=100)
        self.tally.set("规划中…")
        threading.Thread(target=self._worker, args=(opts,),
                         daemon=True).start()

    def _collect_opts(self, dry: bool) -> dict:
        delete = None
        sources = []
        if self.mode.get() == "delete":
            delete = [p.strip() for p in
                      self.del_text.get("1.0", "end").splitlines() if p.strip()]
            if not delete:
                raise ValueError("请填写要删除的仓库路径。")
        else:
            sources = [s.strip() for s in self.src_var.get().split("|")
                       if s.strip()]
            if not sources:
                raise ValueError("请添加要提交的源文件或目录。")
        return {"sources": sources, "delete": delete,
                "dest": self.dest.get().strip(), "title":
                self.title.get().strip() or None, "dry_run": dry or
                self.dry.get(), "workers": int(self.workers.get() or 0)}

    def _worker(self, opts: dict):
        try:
            self.res = run_plan(opts, GuiReporter(self.q))
        except Exception as e:  # 参数/网络等错误 → 日志窗口展示
            self.q.put(("log", {"text": f"错误: {e}"}))
            self.q.put(("done", {"ok": False}))

    def _poll(self):
        try:
            while True:
                ev, kw = self.q.get_nowait()
                getattr(self, f"_on_{ev}")(**kw)
        except queue.Empty:
            pass
        self.root.after(120, self._poll)

    # ------------------------------------------------------- 进度事件处理
    def _on_log(self, text: str):
        self._log(text)

    def _on_plan(self, total: int, steps: int):
        self.bar.configure(maximum=steps, value=0)
        self.tally.set(f"0/{steps} 个 PR · 任务 {total}")
        self._log(f"共 {total} 个任务、{steps} 个 PR")

    def _on_unit(self, idx: int, label: str, steps: int):
        self.tree.insert("", "end", iid=str(idx),
                         values=(label, f"0/{steps}", "等待中"))

    def _on_step(self, idx: int, k: int, pr: int, url: str):
        vals = list(self.tree.item(str(idx), "values"))
        total = int(vals[1].split("/")[1])
        self.tree.set(str(idx), "progress", f"{k}/{total}")
        self.bar.configure(value=self.bar["value"] + 1)
        merged = sum(1 for iid in self.tree.get_children()
                     if self.tree.set(iid, "status") == "merged")
        self.tally.set(f"{int(self.bar['value'])}/{self.bar['maximum']} 个 PR"
                       f" · 合并 {merged}")

    def _on_unit_done(self, idx: int, label: str, status: str):
        tag = {"merged": "ok", "timeout": "warn"}.get(status, "bad")
        self.tree.set(str(idx), "status", status)
        self.tree.item(str(idx), tags=(tag,))

    def _on_done(self, ok: bool):
        self.running = False
        self.btn_dry.configure(state="normal")
        self.btn_go.configure(state="normal")
        self.status.set("完成 ✓" if ok else "完成（有失败项，见日志）")
        if ok:
            self._log("全部完成 ✓")


def main():
    root = tk.Tk()
    try:
        ttk.Style().theme_use("vista")
    except tk.TclError:
        pass
    App(root)
    root.mainloop()
