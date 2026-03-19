import { useState, useEffect } from "react";

import { Button } from "@/components/ui/button";
import { ChatComponent } from "@/components/ChatComponent";

function App() {
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);

  useEffect(() => {
    fetch("/api/chat", { method: "HEAD" }).then((res) => {
      setAuthenticated(res.ok || res.status !== 401);
    }).catch(() => {
      setAuthenticated(false);
    });
  }, []);

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

  return (
    <div className="h-screen flex flex-col">
      <header className="border-b px-4 py-3 flex items-center justify-between">
        <h1 className="text-lg font-semibold">Travel AI Chat</h1>
      </header>
      <main className="flex-1 overflow-hidden">
        <ChatComponent />
      </main>
    </div>
  );
}

export default App;
