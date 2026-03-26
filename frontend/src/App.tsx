import { useState, useEffect, useCallback } from "react";

import { Button } from "@/components/ui/button";
import { ChatComponent } from "@/components/ChatComponent";

interface Preferences {
  budget?: number;
  style?: string;
  roomType?: string;
  amenities?: string[];
}

interface HistoryEntry {
  id: number;
  query: string;
  resultSummary: string;
  timestamp: string;
}

const STYLE_ICONS: Record<string, string> = {
  beach: "🏖️",
  city: "🏙️",
  mountain: "⛰️",
  luxury: "✨",
  boutique: "🏡",
};

const AMENITY_ICONS: Record<string, string> = {
  pool: "🏊",
  wifi: "📶",
  parking: "🅿️",
  gym: "💪",
  breakfast: "🍳",
  spa: "💆",
  airConditioning: "❄️",
  petFriendly: "🐾",
  kitchen: "🍳",
  freeCancellation: "✅",
};

function App() {
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [preferences, setPreferences] = useState<Preferences>({});
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [selectedQuery, setSelectedQuery] = useState<string | null>(null);

  const fetchPreferences = useCallback(() => {
    fetch("/api/preferences")
      .then((res) => (res.ok ? res.json() : {}))
      .then(setPreferences)
      .catch(() => {});
  }, []);

  const fetchHistory = useCallback(() => {
    fetch("/api/history")
      .then((res) => (res.ok ? res.json() : []))
      .then(setHistory)
      .catch(() => {});
  }, []);

  const fetchAll = useCallback(() => {
    fetchPreferences();
    fetchHistory();
  }, [fetchPreferences, fetchHistory]);

  useEffect(() => {
    fetch("/api/chat", { method: "HEAD" })
      .then((res) => {
        const isAuth = res.ok || res.status !== 401;
        setAuthenticated(isAuth);
        if (isAuth) fetchAll();
      })
      .catch(() => {
        setAuthenticated(false);
      });
  }, [fetchAll]);

  if (authenticated === null) {
    return (
      <div className="h-screen flex items-center justify-center">
        <p className="text-muted-foreground">Loading...</p>
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="h-screen flex flex-col items-center justify-center gap-4">
        <h1 className="text-4xl font-bold">Travel AI Chat</h1>
        <p className="text-muted-foreground">
          Find hotels with the power of AI and Trivago.
        </p>
        <Button asChild size="lg">
          <a href="/oauth2/authorization/google">Login with Google</a>
        </Button>
      </div>
    );
  }

  const hasPreferences =
    preferences.budget ||
    preferences.style ||
    preferences.roomType ||
    (preferences.amenities && preferences.amenities.length > 0);

  return (
    <div className="h-screen flex flex-col">
      <header className="border-b px-4 py-3 space-y-2">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold">Travel AI Chat</h1>
          {hasPreferences && (
            <div className="flex items-center gap-2 flex-wrap">
              {preferences.budget && (
                <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium">
                  💰 €{preferences.budget}
                </span>
              )}
              {preferences.style && (
                <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium">
                  {STYLE_ICONS[preferences.style] || "🏨"} {preferences.style}
                </span>
              )}
              {preferences.roomType && (
                <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium">
                  🛏️ {preferences.roomType}
                </span>
              )}
              {preferences.amenities?.map((amenity) => (
                <span
                  key={amenity}
                  className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium"
                >
                  {AMENITY_ICONS[amenity] || "•"} {amenity}
                </span>
              ))}
            </div>
          )}
        </div>
        {history.length > 0 && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground overflow-x-auto">
            <span className="shrink-0">Recent:</span>
            {history
              .slice(-5)
              .reverse()
              .map((entry) => (
                <button
                  key={entry.id}
                  onClick={() => setSelectedQuery(entry.query)}
                  className="shrink-0 px-2 py-0.5 rounded-full border border-border hover:bg-accent hover:text-accent-foreground transition-colors cursor-pointer"
                >
                  {entry.query.length > 30
                    ? entry.query.substring(0, 30) + "..."
                    : entry.query}
                </button>
              ))}
          </div>
        )}
      </header>
      <main className="flex-1 overflow-hidden">
        <ChatComponent
          onMessageSent={fetchAll}
          selectedQuery={selectedQuery}
          onSelectedQueryHandled={() => setSelectedQuery(null)}
        />
      </main>
    </div>
  );
}

export default App;
