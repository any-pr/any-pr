namespace ProcessSH.Models;

public sealed class Suggestion : IEquatable<Suggestion>
{
    public string Text { get; }
    public SuggestionType Type { get; }
    public int Priority { get; }

    public Suggestion(string text, SuggestionType type)
    {
        Text = text;
        Type = type;
        Priority = type switch
        {
            SuggestionType.History => 300,
            SuggestionType.Command => 200,
            SuggestionType.Path => 100,
            _ => 0,
        };
    }

    public Suggestion(string text, SuggestionType type, int historyCount)
    {
        Text = text;
        Type = type;
        var safeCount = Math.Clamp(historyCount, 0, 100);
        Priority = type switch
        {
            SuggestionType.History => 300 + safeCount,
            SuggestionType.Command => 200,
            SuggestionType.Path => 100,
            _ => 0,
        };
    }

    public bool Equals(Suggestion? other)
    {
        if (other is null) return false;
        return Text == other.Text && Type == other.Type;
    }

    public override bool Equals(object? obj) => Equals(obj as Suggestion);

    public override int GetHashCode() => HashCode.Combine(Text, Type);

    /// <summary>
    /// AutoSuggestBox 在某些路径下会调用 ToString() 显示/填入文本。
    /// 必须返回 Text，否则会显示 "ProcessSH.Models.Suggestion"。
    /// </summary>
    public override string ToString() => Text;

    public string IconGlyph => Type switch
    {
        SuggestionType.History => "\xE81C",
        SuggestionType.Command => "\xE756",
        SuggestionType.Path => "\xE8B7",
        _ => "\xE9CE",
    };
}

public enum SuggestionType
{
    History,
    Command,
    Path,
}