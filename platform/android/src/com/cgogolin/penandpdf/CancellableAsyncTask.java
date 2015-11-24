package com.cgogolin.penandpdf;

import android.os.AsyncTask;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

// Ideally this would be a subclass of AsyncTask, however the cancel() method is final, and cannot
// be overridden. I felt that having two different, but similar cancel methods was a bad idea.
public class CancellableAsyncTask<Params, Result>
{
	private final AsyncTask<Params, Void, Result> asyncTask;
	private final CancellableTaskDefinition<Params, Result> ourTask;

	protected void onPreExecute()
	{

	}

	protected void onPostExecute(Result result)
	{

	}

	protected void onCanceled()
    {
            
	}
    
	public CancellableAsyncTask(final CancellableTaskDefinition<Params, Result> task)
	{
		if (task == null)
				throw new IllegalArgumentException();

		this.ourTask = task;
		asyncTask = new AsyncTask<Params, Void, Result>()
				{
					@Override
					protected Result doInBackground(Params... params)
					{
						return task.doInBackground(params);
					}

					@Override
					protected void onPreExecute()
					{
						CancellableAsyncTask.this.onPreExecute();
					}

					@Override
					protected void onPostExecute(Result result)
					{
						CancellableAsyncTask.this.onPostExecute(result);
						task.doCleanup();
					}

						//We rather do the cleanup here so that we can cancel the task with cancel() from the UI thread 
					@Override
					protected void onCancelled(Result result)
					{
						cleanUp();
					}
				};
	}

	public void cancel()
	{
		this.asyncTask.cancel(true);
		ourTask.doCancel();
	}

	private void cleanUp()
	{
		ourTask.doCleanup();
	}
	
	public void cancelAndWait()
	{
		cancel();

		try
		{
			this.asyncTask.get();
		}
		catch (InterruptedException e)
		{
		}
		catch (ExecutionException e)
		{
		}
		catch (CancellationException e)
		{
		}

		cleanUp();
	}

	public void execute(Params ... params)
	{
		asyncTask.execute(params);
	}

}
