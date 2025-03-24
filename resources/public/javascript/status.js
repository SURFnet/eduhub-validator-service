// SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileContributor: Michiel de Mare

// Define the endpoint and polling interval (in milliseconds)
const rootUrl = `${window.location.protocol}//${window.location.host}`;
const endpoint = `${rootUrl}/status/${validationUuid}`; // Replace with your actual endpoint
const pollInterval = 2000; // Poll every 5 seconds

// Reload page and stop polling when status no longer pending
function pollJobStatus() {
    fetch(endpoint)
        .then(response => response.json())
        .then(data => {
            if (data['job-status'] !== 'pending') {
                // Stop polling
                clearInterval(polling);
                document.location.reload();
            }
        })
        .catch(error => console.error('Error fetching job status:', error));
}
