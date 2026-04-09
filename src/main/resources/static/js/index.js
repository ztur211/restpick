let debounceTimer;
let selectedLocation = null; // Store the selected location for biasing autocomplete results

const searchInput = document.getElementById('address-input');
const autocompleteWrapper = document.getElementById('autocomplete-wrapper');

// Listen for user typing in the search bar
searchInput.addEventListener('input', () => {
    const input = searchInput.value.trim();

    selectedLocation = null; // Reset selected location when user types
    removeActionButton();
    clearSuggestions();
    autocompleteWrapper.classList.toggle('is-open', false);

    if (input.length < 3) {
        return; // Don't fetch suggestions for very short input
    }

    const requestBody = { input };

    clearTimeout(debounceTimer); // Clear the previous timer
    debounceTimer = setTimeout(async() => {
        // If a location is selected, bias the autocomplete results towards that location
        if (selectedLocation) {
            requestBody.biasLatitude = selectedLocation.latitude;
            requestBody.biasLongitude = selectedLocation.longitude;
        }
        try {
            const response = await fetch('/autocomplete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });
            const suggestions = await response.json();
            renderSuggestions(suggestions);
            console.log("Response:", response);
        } catch (error) {
            console.error('Autocomplete error:', error);
        }
    }, 300);
});

document.addEventListener('mousedown', (e) => {
    if (!e.target.closest('#autocomplete-wrapper')) {
        autocompleteWrapper.classList.toggle('is-open', false);
        clearSuggestions();
    }
});

function renderSuggestions(suggestions) {
    clearSuggestions();

    if (!suggestions || suggestions.length === 0) return;

    const list = document.createElement('ul');
    list.id = 'autocomplete-list';
    list.classList.add('bg-white', 'list-unstyled', 'position-absolute', 'border', 'w-100', 'mt-0', 'overflow-hidden');
    console.log('Rendering suggestions:', suggestions);
    

    suggestions.forEach(s => {
        const item = document.createElement('li');
        item.className = 'autocomplete-item';
        item.textContent = s.address;
        item.addEventListener('click', async () => {
            searchInput.value = s.address;

            try {
                const response = await fetch('/resolve-location', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ placeId: s.placeId })
                });

                if (!response.ok) throw new Error("Failed to resolve location");

                selectedLocation = await response.json();

                showActionButton(); // <-- Button appears here

            } catch (err) {
                console.error("Error resolving location:", err);
            }
        });
        list.appendChild(item);
    });
    document.getElementById('autocomplete-wrapper').appendChild(list);
    
    autocompleteWrapper.classList.toggle('is-open', true);
    console.log("Appending list:", list);

}

async function onSuggestionSelected(suggestion) {
    removeActionButton();
    searchInput.value = suggestion.address;
    clearSuggestions();
    autocompleteWrapper.classList.toggle('is-open', false);

    try {
        const response = await fetch('/resolve-location', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ placeId: suggestion.placeId })
        });

        if (!response.ok) throw new Error('Failed to resolve location');

        selectedLocation = await response.json();
        showActionButton();
        searchInput.addEventListener('input', () => {
            removeActionButton();   // Hide button while typing
            selectedLocation = null; // Address is no longer valid
        });

    } catch (error) {
        console.error('Error resolving location:', error);
        removeActionButton();
    }
}

function showActionButton() {
    removeActionButton(); // Ensure no duplicate buttons

    const button = document.createElement('button');
    button.id = 'pick-restaurant-btn';
    button.type = 'button';
    button.className = 'btn btn-primary mt-3';
    button.textContent = 'Pick a Restaurant for Me!';
    document.getElementById('button-container').appendChild(button);

    button.addEventListener('click', async () => {
        if (!selectedLocation) return;

        // Collect all filter values
        const radiusMiles = document.querySelector('input[name="radius"]:checked')?.value || '';
        const cuisine = document.querySelector('input[name="cuisine"]:checked')?.value || '';
        const price = document.querySelector('input[name="price"]:checked')?.value || '';
        const minRating = document.querySelector('input[name="minRating"]:checked')?.value || '';
        const openNow = document.querySelector('input[name="openNow"]:checked')?.value === 'true';

        const requestBody = {
            "locationRestriction": {
                "circle": {
                    "center": {
                        "latitude": selectedLocation.latitude,
                        "longitude": selectedLocation.longitude
                    },
                    "radius": parseFloat(radiusMiles)
                }
            },
            "types": cuisine ? [cuisine] : [],
            "priceLevel": price ? [price] : [],
            "rating": minRating ? parseFloat(minRating) : null,
            "openNow": openNow
        };
        console.log(requestBody);

        try {
            const response = await fetch('/pick', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const err = await response.json();
                showResult(`Error: ${err.error}`);
                return;
            }

            const restaurant = await response.json();
            showResult(restaurant);

        } catch (error) {
            console.error('Error sending address:', error);
        }
    });
}

function removeActionButton() {
    const existingButton = document.getElementById('pick-restaurant-btn');
    if (existingButton) {
        existingButton.remove();
    }
}

function clearSuggestions() {
    const existingList = document.getElementById('autocomplete-list');
    if (existingList) {
        existingList.remove();
    }
}

// Update all dropdown button labels when a radio is selected
['cuisine', 'minRating', 'price', 'openNow', 'radius'].forEach(name => {
    document.querySelectorAll(`input[name="${name}"]`).forEach(radio => {
        radio.addEventListener('change', () => {
            const labelText = document.querySelector(`label[for="${radio.id}"]`).textContent.trim();
            const buttonMap = {
                cuisine:   'cuisine-dropdown',
                minRating: 'rating-dropdown',
                price:     'price-dropdown',
                openNow:   'hours-dropdown',
                radius:    'radius-dropdown'
            };
            const btn = document.getElementById(buttonMap[name]);
            btn.querySelector('span').textContent = labelText;
        });
    });
});

function formatPriceLevel(priceLevel) {
    const priceLevels = {
        'PRICE_LEVEL_UNSPECIFIED': 'Any Price',
        'PRICE_LEVEL_INEXPENSIVE': '$',
        'PRICE_LEVEL_MODERATE': '$$',
        'PRICE_LEVEL_EXPENSIVE': '$$$',
        'PRICE_LEVEL_VERY_EXPENSIVE': '$$$$'
    };
    return priceLevels[priceLevel] ?? 'N/A';
}

function showResult(restaurant) {
    const resultDiv = document.getElementById('result-container');
    
    if (typeof restaurant === 'string') {
        resultDiv.innerHTML = `<div class="alert alert-info" role="alert">${restaurant}</div>`;
        return;
    }
    resultDiv.innerHTML = `
        <h2>${restaurant.displayName}</h2>
        <p>Rating: ${restaurant.rating ?? 'N/A'}</p>
        <p>${restaurant.formattedAddress}</p>
        <p>Price: ${formatPriceLevel(restaurant.priceLevel)}</p>
        ${restaurant.websiteUri ? `<a href="${restaurant.websiteUri}" target="_blank">Visit Website</a>` : ''}
    `;
}