import { FormEvent, useMemo, useState } from 'react';
import { Schedule } from '../types';

type IntervalOption = Schedule['interval'];

const intervalOptions: IntervalOption[] = ['Hourly', 'Daily', 'Weekly'];

const initialSchedules: Schedule[] = [
  {
    id: 'schedule-1',
    name: 'Daily product crawl',
    interval: 'Daily',
    status: 'active',
    nextRun: new Date(Date.now() + 1000 * 60 * 60 * 8).toISOString(),
    lastRun: new Date(Date.now() - 1000 * 60 * 60 * 16).toISOString()
  },
  {
    id: 'schedule-2',
    name: 'Weekly docs recrawl',
    interval: 'Weekly',
    status: 'paused',
    nextRun: new Date(Date.now() + 1000 * 60 * 60 * 24 * 6).toISOString()
  }
];

export default function ScheduleConfig() {
  const [schedules, setSchedules] = useState<Schedule[]>(initialSchedules);
  const [name, setName] = useState('');
  const [interval, setInterval] = useState<IntervalOption>('Daily');
  const [editingId, setEditingId] = useState<string | null>(null);

  const formTitle = useMemo(() => (editingId ? 'Edit schedule' : 'Create schedule'), [editingId]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!name.trim()) {
      return;
    }

    if (editingId) {
      setSchedules((currentSchedules) =>
        currentSchedules.map((schedule) =>
          schedule.id === editingId ? { ...schedule, name: name.trim(), interval } : schedule
        )
      );
      setEditingId(null);
    } else {
      setSchedules((currentSchedules) => [
        {
          id: `schedule-${Date.now()}`,
          name: name.trim(),
          interval,
          status: 'active',
          nextRun: new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString()
        },
        ...currentSchedules
      ]);
    }

    setName('');
    setInterval('Daily');
  };

  const handleEdit = (schedule: Schedule) => {
    setEditingId(schedule.id);
    setName(schedule.name);
    setInterval(schedule.interval);
  };

  const handleDelete = (scheduleId: string) => {
    setSchedules((currentSchedules) => currentSchedules.filter((schedule) => schedule.id !== scheduleId));
    if (editingId === scheduleId) {
      setEditingId(null);
      setName('');
      setInterval('Daily');
    }
  };

  const handlePauseToggle = (scheduleId: string) => {
    setSchedules((currentSchedules) =>
      currentSchedules.map((schedule) =>
        schedule.id === scheduleId
          ? { ...schedule, status: schedule.status === 'active' ? 'paused' : 'active' }
          : schedule
      )
    );
  };

  const handleTrigger = (scheduleId: string) => {
    setSchedules((currentSchedules) =>
      currentSchedules.map((schedule) =>
        schedule.id === scheduleId
          ? {
              ...schedule,
              lastRun: new Date().toISOString(),
              nextRun: new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString()
            }
          : schedule
      )
    );
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">{formTitle}</h2>
        <p className="mt-1 text-sm text-slate-500">Configure recurring crawl runs and execution cadence.</p>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <div>
            <label htmlFor="schedule-name" className="block text-sm font-medium text-slate-700">
              Schedule name
            </label>
            <input
              id="schedule-name"
              type="text"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Daily crawl for marketing site"
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>

          <div>
            <label htmlFor="schedule-interval" className="block text-sm font-medium text-slate-700">
              Interval
            </label>
            <select
              id="schedule-interval"
              value={interval}
              onChange={(event) => setInterval(event.target.value as IntervalOption)}
              className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            >
              {intervalOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              className="rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700"
            >
              {editingId ? 'Update schedule' : 'Create schedule'}
            </button>
            {editingId && (
              <button
                type="button"
                onClick={() => {
                  setEditingId(null);
                  setName('');
                  setInterval('Daily');
                }}
                className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Active Schedules</h2>
        <p className="mt-1 text-sm text-slate-500">Pause, edit, delete, or manually trigger any configured schedule.</p>

        <div className="mt-6 space-y-4">
          {schedules.map((schedule) => (
            <div key={schedule.id} className="rounded-xl border border-slate-200 p-4">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-base font-semibold text-slate-900">{schedule.name}</h3>
                    <span
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        schedule.status === 'active'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-amber-100 text-amber-700'
                      }`}
                    >
                      {schedule.status}
                    </span>
                  </div>
                  <p className="mt-2 text-sm text-slate-500">
                    {schedule.interval} cadence · Next run {new Date(schedule.nextRun).toLocaleString()}
                  </p>
                  <p className="mt-1 text-xs text-slate-400">
                    Last run {schedule.lastRun ? new Date(schedule.lastRun).toLocaleString() : 'Not run yet'}
                  </p>
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => handleEdit(schedule)}
                    className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={() => handlePauseToggle(schedule.id)}
                    className="rounded-lg border border-amber-300 px-3 py-2 text-xs font-semibold text-amber-700 hover:bg-amber-50"
                  >
                    {schedule.status === 'active' ? 'Pause' : 'Resume'}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleTrigger(schedule.id)}
                    className="rounded-lg border border-blue-300 px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-50"
                  >
                    Trigger
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(schedule.id)}
                    className="rounded-lg border border-rose-300 px-3 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-50"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}

          {schedules.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
              No schedules configured yet.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
