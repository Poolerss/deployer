import { useEffect, useRef } from "react";
import { LogEvent } from "./api";

type Props = {
  title: string;
  badge: string;
  empty: string;
  lines: LogEvent[];
  className?: string;
};

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value.slice(11, 19) || value;
  }
  return date.toLocaleTimeString("ru-RU", { hour12: false });
}

export default function LogFrame({ title, badge, empty, lines, className = "" }: Props) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = ref.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [lines]);

  return (
    <section className={`console-wrap ${className}`.trim()}>
      <div className="console-head">
        <span>{title}</span>
        <code>{badge}</code>
      </div>
      <div className="console" ref={ref}>
        {lines.length === 0 ? (
          <p className="placeholder">{empty}</p>
        ) : (
          lines.map((line, index) => (
            <p key={`${line.ts}-${index}`} className={`line line-${line.stream}`}>
              <time>{formatTime(line.ts)}</time>
              <span>{line.text}</span>
            </p>
          ))
        )}
      </div>
    </section>
  );
}
