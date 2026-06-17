import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import LoadingSpinner from './LoadingSpinner';
import { useToast } from './ToastProvider';
import {
  createSchedule,
  deleteSchedule,
  getJobs,
  getSchedules,
  triggerSchedule,
  updateSchedule
} from '../services/apiClient';
import { Job, Schedule, ScheduleIntervalType, SchedulePayload } from '../types';

type IntervalPreset = '1H' | '6H' | '12H' | 'DAILY' | 'WEEKLY' | 'CUSTOM_CRON';

interface SeedSourceOption {
  source: string;
  fileName: string;
  createdAt: string;
}

interface FormState {
  seedFileId: string;
  intervalPreset: IntervalPreset;
  customCron: string;
  enabled: boolean;
}

const sixHourCron = '0 0 */6 * * *';
const twelveHourCron = '0 0 */12 * * *';

const defaultFormState: FormState = {
  seedFileId: '',
  intervalPreset: 'DAILY',
  customCron: '',
  enabled: true
};

const formatInterval = (schedule: Schedule) => {
  if (schedule.intervalType === 'CRON') {
    if (schedule.cronExpression === sixHourCron) {
      return 'Every 6 hours';
    }

    if (schedule.cronExpression === twelveHourCron) {
      return 'Every 12 hours';
    }

    return `Custom cron (${schedule.cronExpression ?? 'n/a'})`;
  }

  if (schedule.intervalType === 'HOURLY') {
    return 'Every hour';
  }

  if (schedule.intervalType === 'DAILY') {
    return 'Daily';
  }

  if (schedule.intervalType === 'WEEKLY') {
    return 'Weekly';
  }

  return schedule.intervalType;
};

const scheduleToPreset = (schedule: Schedule): IntervalPreset => {
  if (schedule.intervalType === 'HOURLY') {
    return '1H';
  }

  if (schedule.intervalType === 'DAILY') {
    return 'DAILY';
  }

  if (schedule.intervalType === 'WEEKLY') {
    return 'WEEKLY';
  }

  if (schedule.intervalType === 'CRON' && schedule.cronExpression === sixHourCron) {
    return '6H';
  }

  if (schedule.intervalType === 'CRON' && schedule.cronExpression === twelveHourCron) {
    return '12H';
  }

  return 'CUSTOM_CRON';
};

const toPayload = (formState: FormState): SchedulePayload => {
  let intervalType: ScheduleIntervalType = 'DAILY';
  let cronExpression = '';

  switch (formState.intervalPreset) {
    case '1H':
      intervalType = 'HOURLY';
      break;
    case '6H':
      intervalType = 'CRON';
      cronExpression = sixHourCron;
      break;
    case '12H':
      intervalType = 'CRON';
      cronExpression = twelveHourCron;
      break;
    case 'WEEKLY':
      intervalType = 'WEEKLY';
      break;
    case 'CUSTOM_CRON':
      intervalType = 'CRON';
      cronExpression = formState.customCron.trim();
      break;
    default:
      intervalType = 'DAILY';
      break;
  }

  return {
    seedFileId: formState.seedFileId,
    intervalType,
    cronExpression,
    enabled: formState.enabled
  };
};

const getFileNameFromSource = (source: string) => source.split('/').pop() ?? source;
const sortByCreatedAt = (jobs: Job[]) =>
  [...jobs].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());

export default function ScheduleConfig() {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [formState, setFormState] = useState<FormState>(defaultFormState);
  const [editingScheduleId, setEditingScheduleId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [busyScheduleId, setBusyScheduleId] = useState<string | null>(null);
  const { showToast } = useToast();

  const loadData = useCallback(async () => {
    const [scheduleResponse, jobsResponse] = await Promise.all([getSchedules(), getJobs()]);
    setSchedules(scheduleResponse);
    setJobs(sortByCreatedAt(jobsResponse));
  }, []);

  useEffect(() => {
    let mounted = true;

    void (async () => {
      try {
        await loadData();
      } catch (error) {
        if (mounted) {
          showToast(error instanceof Error ? error.message : 'Unable to load schedules.', 'error', 'Schedules unavailable');
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      mounted = false;
    };
  }, [loadData, showToast]);

  const seedOptions = useMemo<SeedSourceOption[]>(
    () =>
      Object.values(
        jobs.reduce<Record<string, SeedSourceOption>>((accumulator, job) => {
          if (!job.source) {
            return accumulator;
          }

          accumulator[job.source] = {
            source: job.source,
            fileName: getFileNameFromSource(job.source),
            createdAt: job.createdAt
          };
          return accumulator;
        }, {})
      ).sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()),
    [jobs]
  );

  const resetForm = () => {
    setFormState(defaultFormState);
    setEditingScheduleId(null);
  };

  const getSourceForSchedule = useCallback(
    (schedule: Schedule) =>
      seedOptions.find((option) => option.fileName === schedule.seedFileName)?.source ?? '',
    [seedOptions]
  );

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!formState.seedFileId) {
      showToast('Select a previously uploaded seed file before saving the schedule.', 'warning', 'Seed file required');
      return;
    }

    const payload = toPayload(formState);

    if (payload.intervalType === 'CRON' && !payload.cronExpression?.trim()) {
      showToast('Provide a valid cron expression for custom cron schedules.', 'warning', 'Cron required');
      return;
    }

    setIsSaving(true);

    try {
      const savedSchedule = editingScheduleId
        ? await updateSchedule(editingScheduleId, payload)
        : await createSchedule(payload);

      setSchedules((current) => {
        const next = editingScheduleId
          ? current.map((schedule) => (schedule.id === savedSchedule.id ? savedSchedule : schedule))
          : [savedSchedule, ...current];

        return next;
      });

      resetForm();
      showToast(
        editingScheduleId ? 'Schedule updated successfully.' : 'Schedule created successfully.',
        'success',
        editingScheduleId ? 'Schedule updated' : 'Schedule created'
      );
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to save schedule.', 'error', 'Save failed');
    } finally {
      setIsSaving(false);
    }
  };

  const handleEdit = (schedule: Schedule) => {
    const seedFileId = getSourceForSchedule(schedule);

    setEditingScheduleId(schedule.id);
    setFormState({
      seedFileId,
      intervalPreset: scheduleToPreset(schedule),
      customCron: schedule.cronExpression ?? '',
      enabled: schedule.enabled
    });

    if (!seedFileId) {
      showToast(
        'The original seed source could not be resolved from existing jobs. Select a replacement seed source before saving.',
        'warning',
        'Seed source needed'
      );
    }
  };

  const handleToggle = async (schedule: Schedule) => {
    const seedFileId = getSourceForSchedule(schedule);

    if (!seedFileId) {
      showToast('Re-upload or select a matching seed source before updating this schedule.', 'error', 'Update blocked');
      return;
    }

    setBusyScheduleId(schedule.id);

    try {
      const updatedSchedule = await updateSchedule(schedule.id, {
        seedFileId,
        intervalType: schedule.intervalType as ScheduleIntervalType,
        cronExpression: schedule.cronExpression ?? '',
        enabled: !schedule.enabled
      });

      setSchedules((current) => current.map((item) => (item.id === updatedSchedule.id ? updatedSchedule : item)));
      showToast(
        updatedSchedule.enabled ? 'Schedule resumed.' : 'Schedule paused.',
        updatedSchedule.enabled ? 'success' : 'warning',
        updatedSchedule.enabled ? 'Schedule active' : 'Schedule paused'
      );
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to update schedule.', 'error', 'Toggle failed');
    } finally {
      setBusyScheduleId(null);
    }
  };

  const handleTrigger = async (scheduleId: string) => {
    setBusyScheduleId(scheduleId);

    try {
      const job = await triggerSchedule(scheduleId);
      showToast(`Triggered schedule and created job ${job.id}.`, 'success', 'Schedule triggered');
      await loadData();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to trigger schedule.', 'error', 'Trigger failed');
    } finally {
      setBusyScheduleId(null);
    }
  };

  const handleDelete = async (scheduleId: string) => {
    if (!window.confirm('Delete this schedule? This action cannot be undone.')) {
      return;
    }

    setBusyScheduleId(scheduleId);

    try {
      await deleteSchedule(scheduleId);
      setSchedules((current) => current.filter((schedule) => schedule.id !== scheduleId));
      if (editingScheduleId === scheduleId) {
        resetForm();
      }
      showToast('Schedule deleted successfully.', 'success', 'Schedule removed');
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Unable to delete schedule.', 'error', 'Delete failed');
    } finally {
      setBusyScheduleId(null);
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{editingScheduleId ? 'Edit schedule' : 'Create schedule'}</h2>
            <p className="mt-1 text-sm text-slate-500">Configure recurring crawl runs through the API Gateway.</p>
          </div>
          {isSaving && <LoadingSpinner size="sm" className="text-blue-600" />}
        </div>

        {seedOptions.length === 0 && (
          <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            No reusable seed files found yet. Upload one from the <Link to="/" className="font-semibold underline">dashboard</Link> by starting a crawl job first.
          </div>
        )}

        <form className="mt-6 space-y-5" onSubmit={(event) => void handleSubmit(event)}>
          <div>
            <label htmlFor="seed-file" className="block text-sm font-medium text-slate-700">
              Seed file source
            </label>
            <select
              id="seed-file"
              value={formState.seedFileId}
              onChange={(event) => setFormState((current) => ({ ...current, seedFileId: event.target.value }))}
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            >
              <option value="">Select a previously uploaded seed file</option>
              {seedOptions.map((option) => (
                <option key={option.source} value={option.source}>
                  {option.fileName} · {new Date(option.createdAt).toLocaleString()}
                </option>
              ))}
            </select>
          </div>

          <fieldset>
            <legend className="block text-sm font-medium text-slate-700">Interval</legend>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {[
                { value: '1H', label: 'Every 1 hour' },
                { value: '6H', label: 'Every 6 hours' },
                { value: '12H', label: 'Every 12 hours' },
                { value: 'DAILY', label: 'Daily' },
                { value: 'WEEKLY', label: 'Weekly' },
                { value: 'CUSTOM_CRON', label: 'Custom cron' }
              ].map((option) => (
                <label
                  key={option.value}
                  className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-sm ${
                    formState.intervalPreset === option.value
                      ? 'border-blue-300 bg-blue-50 text-blue-900'
                      : 'border-slate-200 bg-white text-slate-700'
                  }`}
                >
                  <input
                    type="radio"
                    name="intervalPreset"
                    value={option.value}
                    checked={formState.intervalPreset === option.value}
                    onChange={(event) =>
                      setFormState((current) => ({ ...current, intervalPreset: event.target.value as IntervalPreset }))
                    }
                    className="h-4 w-4 text-blue-600 focus:ring-blue-500"
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>

          {formState.intervalPreset === 'CUSTOM_CRON' && (
            <div>
              <label htmlFor="cron-expression" className="block text-sm font-medium text-slate-700">
                Cron expression
              </label>
              <input
                id="cron-expression"
                type="text"
                value={formState.customCron}
                onChange={(event) => setFormState((current) => ({ ...current, customCron: event.target.value }))}
                placeholder="0 0 */2 * * *"
                className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
              <p className="mt-2 text-xs text-slate-500">Use Spring cron format with six fields: second minute hour day month weekday.</p>
            </div>
          )}

          <label className="flex items-center gap-3 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={formState.enabled}
              onChange={(event) => setFormState((current) => ({ ...current, enabled: event.target.checked }))}
              className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
            />
            Enable this schedule immediately
          </label>

          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={isSaving}
              className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {editingScheduleId ? 'Update schedule' : 'Create schedule'}
            </button>
            {editingScheduleId && (
              <button
                type="button"
                onClick={resetForm}
                className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Active Schedules</h2>
            <p className="mt-1 text-sm text-slate-500">Edit, pause, trigger, or delete existing schedules.</p>
          </div>
          {isLoading && <LoadingSpinner size="sm" className="text-blue-600" />}
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-3 pr-4">Seed file</th>
                <th className="pb-3 pr-4">Interval</th>
                <th className="pb-3 pr-4">Next run</th>
                <th className="pb-3 pr-4">Last status</th>
                <th className="pb-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm text-slate-700">
              {!isLoading && schedules.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-sm text-slate-500">
                    No schedules configured yet.
                  </td>
                </tr>
              )}

              {schedules.map((schedule) => (
                <tr key={schedule.id}>
                  <td className="py-4 pr-4">
                    <div>
                      <p className="font-semibold text-slate-900">{schedule.seedFileName}</p>
                      <p className="text-xs text-slate-500">{schedule.enabled ? 'Enabled' : 'Paused'}</p>
                    </div>
                  </td>
                  <td className="py-4 pr-4">{formatInterval(schedule)}</td>
                  <td className="py-4 pr-4">{schedule.nextRunAt ? new Date(schedule.nextRunAt).toLocaleString() : 'Paused'}</td>
                  <td className="py-4 pr-4">{schedule.lastRunStatus ?? 'NEVER_RUN'}</td>
                  <td className="py-4">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => handleEdit(schedule)}
                        className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleToggle(schedule)}
                        disabled={busyScheduleId === schedule.id}
                        className="rounded-lg border border-amber-300 px-3 py-2 text-xs font-semibold text-amber-700 transition hover:bg-amber-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                      >
                        {busyScheduleId === schedule.id ? 'Working...' : schedule.enabled ? 'Pause' : 'Resume'}
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleTrigger(schedule.id)}
                        disabled={busyScheduleId === schedule.id || !schedule.enabled}
                        className="rounded-lg border border-blue-300 px-3 py-2 text-xs font-semibold text-blue-700 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                      >
                        Trigger Now
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleDelete(schedule.id)}
                        disabled={busyScheduleId === schedule.id}
                        className="rounded-lg border border-rose-300 px-3 py-2 text-xs font-semibold text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
