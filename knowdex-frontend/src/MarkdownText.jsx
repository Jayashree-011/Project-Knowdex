function renderInline(text, keyPrefix = 'i') {
  const nodes = [];
  const pattern = /(\*\*[^*\n]+?\*\*|`[^`\n]+`|\*[^*\n]+?\*)/g;
  let lastIndex = 0;
  let match;
  let idx = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }

    const token = match[0];
    if (token.startsWith('**')) {
      nodes.push(<strong key={`${keyPrefix}-${idx}`}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('`')) {
      nodes.push(<code key={`${keyPrefix}-${idx}`}>{token.slice(1, -1)}</code>);
    } else {
      nodes.push(<em key={`${keyPrefix}-${idx}`}>{token.slice(1, -1)}</em>);
    }

    lastIndex = match.index + token.length;
    idx += 1;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }

  return nodes;
}

function normalizeMarkdown(raw) {
  return String(raw || '')
    .replace(/\r\n/g, '\n')
    .replace(/^```(?:markdown|md)?\s*\n?/i, '')
    .replace(/\n?```\s*$/i, '')
    .replace(/([^\n])\s+(\d+)\.\s+(\*\*)/g, '$1\n$2. $3')
    .replace(/([^\n])\s+([-*•])\s+/g, '$1\n$2 ')
    .trim();
}

function parseBlocks(text) {
  const lines = normalizeMarkdown(text).split('\n');
  const blocks = [];
  let paragraph = [];
  let list = null;

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push({ type: 'p', text: paragraph.join(' ') });
      paragraph = [];
    }
  };

  const flushList = () => {
    if (list) {
      blocks.push(list);
      list = null;
    }
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line) {
      flushParagraph();
      flushList();
      continue;
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      flushParagraph();
      flushList();
      blocks.push({ type: `h${heading[1].length}`, text: heading[2] });
      continue;
    }

    const numbered = line.match(/^(\d+)\.\s+(.+)$/);
    if (numbered) {
      flushParagraph();
      const n = Number(numbered[1]);
      if (!list || list.type !== 'ol' || list.start + list.items.length !== n) {
        flushList();
        list = { type: 'ol', start: n, items: [] };
      }
      list.items.push(numbered[2]);
      continue;
    }

    const bullet = line.match(/^[-*•]\s+(.+)$/);
    if (bullet) {
      flushParagraph();
      if (!list || list.type !== 'ul') {
        flushList();
        list = { type: 'ul', items: [] };
      }
      list.items.push(bullet[1]);
      continue;
    }

    flushList();
    paragraph.push(line);
  }

  flushParagraph();
  flushList();
  return blocks;
}

export default function MarkdownText({ text }) {
  if (!text) return null;

  const blocks = parseBlocks(text);

  return (
    <div className="ai-markdown">
      {blocks.map((block, i) => {
        if (block.type === 'h1' || block.type === 'h2' || block.type === 'h3') {
          const Tag = block.type;
          return <Tag key={i}>{renderInline(block.text, `h-${i}`)}</Tag>;
        }

        if (block.type === 'ul' || block.type === 'ol') {
          const Tag = block.type;
          return (
            <Tag key={i} start={block.start}>
              {block.items.map((item, j) => (
                <li key={j}>{renderInline(item, `l-${i}-${j}`)}</li>
              ))}
            </Tag>
          );
        }

        return <p key={i}>{renderInline(block.text, `p-${i}`)}</p>;
      })}
    </div>
  );
}
