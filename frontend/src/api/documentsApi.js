async function readJson(response) {
  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message ?? 'Document request failed');
  }

  return body;
}

export async function getFolderDocuments(folderId, page = 0, pageSize = 50) {
  const response = await fetch(
    `/api/folders/${folderId}/documents?page=${page}&pageSize=${pageSize}`
  );

  return readJson(response);
}
