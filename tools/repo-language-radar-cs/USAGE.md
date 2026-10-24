# Repo Language Radar

A dependency-free .NET CLI that ranks tracked source files by line count and
byte size, and reports how many additional C# lines are needed to take first
place.

## Run

From the repository root:

```sh
dotnet run --project tools/repo-language-radar-cs
dotnet run --project tools/repo-language-radar-cs -- --json
dotnet run --project tools/repo-language-radar-cs -- --path path/to/repository
```

The tool requires Git and the .NET 10 SDK. It counts recognized source-file
extensions from `git ls-files` and reads their current working-tree contents.
Untracked files are not included. This is a transparent extension-based
estimate, not GitHub Linguist's language statistics. Byte shares include every
recognized tracked source file, including generated or vendored files, unless
the file contains a NUL byte. Generated source is not automatically excluded.
Files containing NUL bytes are skipped.
