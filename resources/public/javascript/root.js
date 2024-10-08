document.addEventListener('DOMContentLoaded', function() {
  // Get the current URL
  const url = window.location.protocol + "//" + window.location.host + window.location.pathname;

  // Update the URL without the query string
  window.history.replaceState({}, document.title, url);

  // Regular expression to validate a UUID (version 4, common format)
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  // Get the input and button elements
  const uuidInput = document.getElementById('uuid-input');
  const checkStatusBtn = document.getElementById('check-status-btn');

  // Enable the button if the UUID is valid
  uuidInput.addEventListener('input', function() {
      const uuidValue = uuidInput.value.trim();
      checkStatusBtn.disabled = !uuidRegex.test(uuidValue);
  });

  // Handle the 'Enter' key press in the input field
  uuidInput.addEventListener('keydown', function(event) {
      if (event.key === 'Enter') {
          event.preventDefault(); // Prevent form submission or default behavior
          if (!checkStatusBtn.disabled) {
              checkStatusBtn.click(); // Simulate button click if button is enabled
          }
      }
  });

  // When the button is clicked, go to the status page with the UUID
  checkStatusBtn.addEventListener('click', function() {
      const uuidValue = uuidInput.value.trim();
      if (uuidRegex.test(uuidValue)) {
          const statusUrl = `${rootUrl}/view/status/${uuidValue}`;
          window.location.href = statusUrl; // Navigate to the status page
      }
  });
});
