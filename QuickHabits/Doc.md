# Pocket Habits

A compact .NET 10 console app for tracking daily habits. It supports creating, listing, searching, renaming, archiving, and logging habits, with streak and activity summaries. Data is stored as JSON in the current user's local application-data folder.

```powershell
dotnet run --project .\QuickHabits.csproj
```

To choose a JSON data file:

```powershell
dotnet run --project .\QuickHabits.csproj -- --data .\habits.json
```

All C# source code is combined into a single physical line in `Program.cs`.
