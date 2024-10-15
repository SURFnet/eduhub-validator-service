// Define the endpoint and polling interval (in milliseconds)
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
