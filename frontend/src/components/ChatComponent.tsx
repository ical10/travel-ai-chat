import { useState, useRef, useEffect, useMemo } from "react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import ReactMarkdown from "react-markdown";

const SUGGESTIONS = [
  "Find hotels in Paris under €150 with a pool",
  "Beach resorts in Bali for 2 adults, budget €100/night",
  "Pet-friendly hotels in Berlin near public transport",
  "Luxury spa hotels in Tokyo under $200",
  "Family-friendly hotels in Barcelona with free breakfast",
  "Cozy stays in Amsterdam with free WiFi and parking",
  "Hotels in Rome near the Colosseum under €120",
  "Mountain lodges in Swiss Alps with gym access",
  "Boutique hotels in Prague for a weekend trip",
  "Hotels in Istanbul with rooftop views under €80",
];

interface Message {
  role: "user" | "assistant";
  content: string;
}

interface ChatComponentProps {
  onMessageSent?: () => void;
}

export function ChatComponent({ onMessageSent }: ChatComponentProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const suggestion = useMemo(
    () => SUGGESTIONS[Math.floor(Math.random() * SUGGESTIONS.length)],
    [],
  );

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userMessage = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: userMessage }]);
    setLoading(true);

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: userMessage,
      });

      if (!res.ok) throw new Error("Chat request failed");

      const content = await res.text();
      setMessages((prev) => [...prev, { role: "assistant", content }]);
      onMessageSent?.();
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: "Sorry, something went wrong. Please try again.",
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-4 max-w-3xl mx-auto">
          {messages.length === 0 && (
            <div className="text-center text-muted-foreground pt-20">
              <h2 className="text-2xl font-semibold mb-2">
                Travel AI Assistant
              </h2>
              <p>Ask me about hotels anywhere in the world.</p>
              <p className="mt-4 text-xs">Try this one 👇</p>
              <button
                onClick={() => setInput(suggestion)}
                className="mt-1 text-sm px-4 py-2 rounded-md border border-border hover:bg-accent hover:text-accent-foreground transition-colors cursor-pointer animate-pulse hover:animate-none"
              >
                &quot;{suggestion}&quot; →
              </button>
            </div>
          )}
          {messages.map((msg, i) => (
            <div
              key={i}
              className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <Card
                className={`px-4 py-3 max-w-[80%] ${
                  msg.role === "user"
                    ? "bg-primary text-primary-foreground"
                    : "bg-card"
                }`}
              >
                {msg.role === "assistant" ? (
                  <ReactMarkdown
                    components={{
                      a: ({ ...props }) => (
                        <a
                          {...props}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-primary underline"
                        />
                      ),
                    }}
                  >
                    {msg.content}
                  </ReactMarkdown>
                ) : (
                  <p>{msg.content}</p>
                )}
              </Card>
            </div>
          ))}
          {loading && (
            <div className="flex justify-start">
              <Card className="px-4 py-3 bg-card">
                <p className="text-muted-foreground animate-pulse">
                  Searching hotels...
                </p>
              </Card>
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      <form
        onSubmit={handleSubmit}
        className="border-t p-4 flex gap-2 max-w-3xl mx-auto w-full"
      >
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about hotels..."
          disabled={loading}
          className="flex-1"
        />
        <Button type="submit" disabled={loading || !input.trim()}>
          Send
        </Button>
      </form>
    </div>
  );
}
