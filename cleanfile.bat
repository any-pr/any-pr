cd /d %~dp0
chcp 936
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do attrib -R -S -A -H "%%d" /S /D
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do rd "%%d"
chcp 950
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do attrib -R -S -A -H "%%d" /S /D
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do rd "%%d"
chcp 65001
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do attrib -R -S -A -H "%%d" /S /D
for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do rd "%%d"