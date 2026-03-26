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
  selectedQuery?: string | null;
  onSelectedQueryHandled?: () => void;
}

export function ChatComponent({
  onMessageSent,
  selectedQuery,
  onSelectedQueryHandled,
}: ChatComponentProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const suggestions = useMemo(() => {
    const shuffled = [...SUGGESTIONS].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, 3);
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  async function sendMessage(message: string) {
    if (!message.trim() || loading) return;

    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: message }]);
    setLoading(true);

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: message,
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

  useEffect(() => {
    if (selectedQuery) {
      sendMessage(selectedQuery);
      onSelectedQueryHandled?.();
    }
  }, [selectedQuery, onSelectedQueryHandled]);

  function handleSubmit(e: React.SubmitEvent) {
    e.preventDefault();
    sendMessage(input.trim());
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-4 max-w-3xl mx-auto">
          {messages.length === 0 && (
            <div className="text-center text-muted-foreground pt-20">
              <h2 className="text-2xl font-semibold mb-2">Travel AI Chat</h2>
              <p>
                Plan your journey with me and I'll give you the best hotel
                offers.
              </p>
              <p className="mt-4 text-xs">Try one of these 👇</p>
              <div className="mt-2 flex flex-col gap-2 items-center">
                {suggestions.map((s) => (
                  <button
                    key={s}
                    onClick={() => sendMessage(s)}
                    className="text-sm px-4 py-2 rounded-md border border-border hover:bg-accent hover:text-accent-foreground transition-colors cursor-pointer"
                  >
                    &quot;{s}&quot; →
                  </button>
                ))}
              </div>
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
                      img: ({ ...props }) => (
                        <img
                          {...props}
                          className="rounded-lg w-full max-h-48 object-cover my-2"
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
