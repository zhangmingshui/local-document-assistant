async function readJson(response) {
  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message ?? 'Mock question request failed');
  }

  return body;
}

export async function askQuestion(payload) {
  console.log('Mock question request', payload);

  const response = await fetch('/api/questions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });
  const body = await readJson(response);

  console.log('Mock question response', body);
  return body;
}
