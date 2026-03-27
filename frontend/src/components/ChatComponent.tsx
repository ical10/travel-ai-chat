import { useState, useRef, useEffect, useMemo } from "react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import ReactMarkdown from "react-markdown";

const SUGGESTIONS = [
  "Find me a hotel near the Eiffel Tower with 2 rooms for 2 adults and 2 children aged 5 and 8.",
  "I need an accommodation in Bali with a pool and high guest rating, from 12 Aug to 20 Aug 2026.",
  "I'm planning a vacation to Japan in October 2026. Find hotels in Tokyo and Osaka for 2 weeks.",
  "Search for a pet-friendly hotel near Old Trafford stadium from 15 May 2026 for 3 nights.",
  "Looking for a budget stay under €80 in Prague with free breakfast, arriving 1 September 2026.",
  "Find a beachfront resort in Santorini for a couple, from 20 June to 25 June 2026.",
  "I want a family-friendly hotel in Barcelona near La Sagrada Familia for the Christmas holidays.",
  "Book a boutique hotel in Amsterdam with parking and WiFi, 10 to 14 November 2026.",
];

interface Accommodation {
  accommodation_name: string;
  accommodation_url: string;
  main_image: string;
  price_per_night: string;
  price_per_stay: string;
  hotel_rating: number;
  review_rating: string;
  review_count: number;
  top_amenities: string;
  distance: string;
}

interface Message {
  role: "user" | "assistant";
  content: string;
  accommodations?: Accommodation[];
}

interface ChatComponentProps {
  onMessageSent?: () => void;
  selectedQuery?: string | null;
  onSelectedQueryHandled?: () => void;
}

function HotelCard({ hotel }: { hotel: Accommodation }) {
  return (
    <a
      href={hotel.accommodation_url}
      target="_blank"
      rel="noopener noreferrer"
      className="block rounded-lg border border-border overflow-hidden hover:shadow-md transition-shadow"
    >
      <img
        src={hotel.main_image}
        alt={hotel.accommodation_name}
        className="w-full h-40 object-cover"
      />
      <div className="p-3 space-y-1">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-semibold text-sm leading-tight">
            {hotel.accommodation_name}
          </h3>
          <span className="shrink-0 font-bold text-sm">
            {hotel.price_per_night}
          </span>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span>{"★".repeat(hotel.hotel_rating)}</span>
          <span>
            {hotel.review_rating}/10 ({hotel.review_count} reviews)
          </span>
        </div>
        <p className="text-xs text-muted-foreground">{hotel.distance}</p>
        <p className="text-xs text-muted-foreground truncate">
          {hotel.top_amenities}
        </p>
      </div>
    </a>
  );
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

      const data = await res.json();
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: data.message,
          accommodations: data.accommodations,
        },
      ]);
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

  function handleSubmit(e: React.FormEvent) {
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
              <div className="max-w-[80%] space-y-3">
                <Card
                  className={`px-4 py-3 ${
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
                {msg.accommodations && msg.accommodations.length > 0 && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {msg.accommodations.map((hotel, j) => (
                      <HotelCard key={j} hotel={hotel} />
                    ))}
                  </div>
                )}
              </div>
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
          placeholder="e.g. I'm looking for a hotel in Berlin from 20 Dec to 24 Dec 2026"
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
