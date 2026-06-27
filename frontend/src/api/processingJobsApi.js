async function readJson(response) {
  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message ?? 'Mock processing request failed');
  }

  return body;
}

export async function getFolders() {
  const response = await fetch('/api/folders');
  return readJson(response);
}

export async function startProcessingJob(payload) {
  console.log('Starting mock processing job request', payload);

  const response = await fetch('/api/processing-jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });
  const body = await readJson(response);

  console.log('Starting mock processing job response', body);
  return body;
}

export async function startProcessingJobForFolder(folderId) {
  console.log('Starting mock rescan request', { folderId });

  const response = await fetch(`/api/folders/${folderId}/processing-jobs`, {
    method: 'POST'
  });
  const body = await readJson(response);

  console.log('Starting mock rescan response', body);
  return body;
}

export async function getProcessingJobStatus({ jobId, pollUrl }) {
  console.log('Refreshing mock processing status request', { jobId, pollUrl });

  const response = await fetch(pollUrl);
  const body = await readJson(response);

  console.log('Refreshing mock processing status response', body);
  return body;
}
