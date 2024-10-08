// Define the endpoint and polling interval (in milliseconds)
const endpoint = `${rootUrl}/status/${validationUuid}`; // Replace with your actual endpoint
const pollInterval = 2000; // Poll every 5 seconds

// Function to handle polling
function pollJobStatus() {
    fetch(endpoint)
        .then(response => response.json())
        .then(data => {
            var status = data['job-status'];
            var statusEl = document.getElementById('job-status');
            statusEl.className = status;
            statusEl.textContent = `Status: ${status}`;
            if (status === 'finished') {
                // Show the existing link tag when job status is 'finished'
                const linkTag = document.getElementById('show-report');
                if (linkTag) {
                    linkTag.style.display = 'inline'; // Change to block or any preferred style
                }

                // Stop polling
                clearInterval(polling);
            }
        })
        .catch(error => console.error('Error fetching job status:', error));
}

// Start polling at defined interval
const polling = setInterval(pollJobStatus, pollInterval);
