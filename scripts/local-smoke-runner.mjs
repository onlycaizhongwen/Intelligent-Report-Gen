import { execFile } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

export function resolveSmokeCommand(step, platform = os.platform()) {
  if (platform === 'win32' && (step.command === 'npm' || step.command.endsWith('.cmd'))) {
    return {
      command: 'cmd.exe',
      args: ['/d', '/s', '/c', [step.command, ...step.args].join(' ')],
    };
  }
  return {
    command: step.command,
    args: step.args,
  };
}

export async function runSmokeStep(step) {
  const { command, args } = resolveSmokeCommand(step);
  const mergedEnv = {
    ...process.env,
    ...step.env,
  };
  const workdir = step.workdir ? path.resolve(process.cwd(), step.workdir) : process.cwd();

  const { stdout, stderr } = await execFileAsync(command, args, {
    cwd: workdir,
    env: mergedEnv,
    windowsHide: os.platform() === 'win32',
  });

  return {
    name: step.name,
    command,
    args,
    workdir,
    stdout: stdout.trim(),
    stderr: stderr.trim(),
  };
}
