import fs from 'node:fs';
import path from 'node:path';

const MAPPING_METHODS = new Map([
  ['GetMapping', 'GET'],
  ['PostMapping', 'POST'],
  ['PutMapping', 'PUT'],
  ['DeleteMapping', 'DELETE'],
  ['PatchMapping', 'PATCH'],
  ['RequestMapping', 'REQUEST'],
]);

export function buildJavaControllerAuthorizationMatrix({ controllersRoot } = {}) {
  if (!controllersRoot) {
    throw new Error('controllersRoot is required');
  }
  return listControllerFiles(controllersRoot)
    .flatMap((filePath) => parseControllerFile(filePath))
    .sort((left, right) => `${left.path} ${left.method}`.localeCompare(`${right.path} ${right.method}`));
}

export function findControllerAuthorizationGaps(matrix) {
  return matrix
    .filter((entry) => entry.boundary === 'unclassified')
    .map((entry) => ({
      method: entry.method,
      path: entry.path,
      controller: entry.controller,
      handler: entry.handler,
      filePath: entry.filePath,
      line: entry.line,
    }));
}

export function findControllerPermissionCatalogGaps(matrix, catalogPermissions) {
  return matrix
    .filter((entry) => entry.boundary === 'permission')
    .filter((entry) => !catalogPermissions.has(entry.permission))
    .map((entry) => ({
      method: entry.method,
      path: entry.path,
      permission: entry.permission,
      controller: entry.controller,
      handler: entry.handler,
      filePath: entry.filePath,
      line: entry.line,
    }));
}

export function renderControllerAuthorizationMatrixMarkdown(matrix) {
  const rows = [
    '# Java Controller Authorization Matrix',
    '',
    '| Method | Path | Boundary | Permission | Controller | Handler |',
    '| --- | --- | --- | --- | --- | --- |',
    ...matrix.map((entry) => `| ${entry.method} | \`${entry.path}\` | ${entry.boundary} | ${entry.permission ?? ''} | ${entry.controller} | ${entry.handler} |`),
    '',
  ];
  return rows.join('\n');
}

function listControllerFiles(root) {
  const absoluteRoot = path.resolve(root);
  const files = [];
  const visit = (directory) => {
    for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
      const itemPath = path.join(directory, item.name);
      if (item.isDirectory()) {
        visit(itemPath);
      } else if (item.isFile() && item.name.endsWith('Controller.java')) {
        files.push(itemPath);
      }
    }
  };
  visit(absoluteRoot);
  return files.sort();
}

function parseControllerFile(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const lines = source.split(/\r?\n/);
  const controller = path.basename(filePath, '.java');
  const basePath = extractClassRequestMapping(lines) ?? '';
  const endpoints = [];
  let annotations = [];

  for (let index = 0; index < lines.length; index += 1) {
    const trimmed = lines[index].trim();
    if (trimmed.startsWith('@')) {
      annotations.push(trimmed);
      continue;
    }

    if (trimmed.includes('class ')) {
      annotations = [];
      continue;
    }

    if (!trimmed.includes('public ') || !trimmed.includes('(')) {
      continue;
    }

    const mapping = extractMethodMapping(annotations);
    if (mapping) {
      const security = extractSecurityBoundary(annotations);
      endpoints.push({
        method: mapping.method,
        path: joinPaths(basePath, mapping.path),
        boundary: security.boundary,
        permission: security.permission,
        controller,
        handler: extractHandlerName(trimmed),
        filePath: path.normalize(filePath),
        line: index + 1,
      });
    }
    annotations = [];
  }

  return endpoints;
}

function extractClassRequestMapping(lines) {
  for (let index = 0; index < lines.length; index += 1) {
    if (!lines[index].includes('class ')) {
      continue;
    }
    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
      const trimmed = lines[cursor].trim();
      if (trimmed.startsWith('@RequestMapping')) {
        return extractAnnotationPath(trimmed);
      }
      if (trimmed && !trimmed.startsWith('@')) {
        break;
      }
    }
  }
  return '';
}

function extractMethodMapping(annotations) {
  for (const annotation of annotations) {
    const match = annotation.match(/^@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)(?:\((.*)\))?/);
    if (!match) {
      continue;
    }
    return {
      method: MAPPING_METHODS.get(match[1]),
      path: extractAnnotationPath(annotation),
    };
  }
  return null;
}

function extractAnnotationPath(annotation) {
  const direct = annotation.match(/\(\s*"([^"]*)"/);
  if (direct) {
    return direct[1];
  }
  const value = annotation.match(/value\s*=\s*"([^"]*)"/);
  if (value) {
    return value[1];
  }
  return '';
}

function extractSecurityBoundary(annotations) {
  for (const annotation of annotations) {
    const permission = annotation.match(/^@RequiresPermission\("([^"]+)"\)/);
    if (permission) {
      return { boundary: 'permission', permission: permission[1] };
    }
    if (annotation.startsWith('@AuthenticatedEndpoint')) {
      return { boundary: 'authenticated', permission: null };
    }
    if (annotation.startsWith('@PublicEndpoint')) {
      return { boundary: 'public', permission: null };
    }
  }
  return { boundary: 'unclassified', permission: null };
}

function extractHandlerName(methodLine) {
  const match = methodLine.match(/\s([A-Za-z_][A-Za-z0-9_]*)\s*\(/);
  return match?.[1] ?? 'unknown';
}

function joinPaths(basePath, methodPath) {
  const joined = `/${[basePath, methodPath].filter(Boolean).join('/')}`;
  return joined.replace(/\/+/g, '/').replace(/\/$/, '') || '/';
}
