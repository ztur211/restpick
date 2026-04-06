let debounceTimer;
let selectedLocation = null; // Store the selected location for biasing autocomplete results

const searchInput = document.getElementById('address-input');

// Listen for user typing in the search bar
searchInput.addEventListener('input', () => {
    const input = searchInput.value.trim();

    selectedLocation = null; // Reset selected location when user types
    removeActionButton();
    clearSuggestions();

    if (input.length < 3) {
        return; // Don't fetch suggestions for very short input
    }

    const requestBody = { input };

    clearTimeout(debounceTimer); // Clear the previous timer
    debounceTimer = setTimeout(async() => {
        // If a location is selected, bias the autocomplete results towards that location
        if (selectedLocation) {
            requestBody.biasLat = selectedLocation.lat;
            requestBody.biasLng = selectedLocation.lng;
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

document.addEventListener('click', (e) => {
    if (!e.target.closest('#autocomplete-wrapper')) {
        clearSuggestions();
    }
});

function renderSuggestions(suggestions) {
    clearSuggestions();

    if (!suggestions || suggestions.length === 0) return;

    const list = document.createElement('ul');
    list.id = 'autocomplete-list';
    list.classList.add('bg-white', 'list-unstyled', 'position-absolute', 'border', 'w-100', 'mt-1', 'overflow-hidden');
    console.log('Rendering suggestions:', suggestions);

    suggestions.forEach(s => {
        const item = document.createElement('li');
        item.textContent = s.address;
        item.addEventListener('click', () => onSuggestionClick(s));
        list.appendChild(item);
    });
    document.getElementById('autocomplete-wrapper').appendChild(list);
}

async function onSuggestionSelected(suggestion) {
    searchInput.value = suggestion.address;
    clearSuggestions();

    try {
        const response = await fetch('/resolve-location', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ placeId: suggestion.placeId })
        });

        if (!response.ok) throw new Error('Failed to resolve location');

        selectedLocation = await response.json();
        showActionButton();
    } catch (error) {
        console.error('Error resolving location:', error);
        removeActionButton();
    }
}

function showActionButton() {
    removeActionButton(); // Ensure no duplicate buttons

    const button = document.createElement('button');
    button.id = 'action-button';
    button.textContent = 'Find Restaurants';
    button.type = 'button';
    document.getElementById('button-container').appendChild(button);

    button.addEventListener('click', async () => {
        if (!selectedLocation) return;

        try {
            const response = await fetch('/restaurants', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ lat: selectedLocation.lat, lng: selectedLocation.lng })
            });

            if (!response.ok) {
                const err = await response.json();
                showResult(`Error: ${err.error}`);
                return;
            }

            const restaurant = await response.json();
            showResult(restaurant);
        } catch (error) {
            console.error('Error fetching restaurants:', error);
            showResult('Failed to fetch restaurant suggestions');
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

function showResult(restaurant) {
    const resultDiv = document.getElementById('result');
    
    if (typeof restaurant === 'string') {
        resultDiv.innerHTML = `<p>${restaurant}</p>`;
        return;
    }
    resultDiv.innerHTML = `
        <h2>${restaurant.displayName}</h2>
        <p>${restaurant.formattedAddress}</p>
        <p>Rating: ${restaurant.rating ?? 'N/A'}</p>
        <p>Price: ${formatPriceLevel(restaurant.priceLevel)}</p>
        ${restaurant.websiteUri ? `<a href="${restaurant.websiteUri}" target="_blank">Visit Website</a>` : ''}
    `;
}

function formatPriceLevel(priceLevel) {
    const priceLevels = {
        'PRICE_LEVEL_UNSPECIFIED': 'Unknown',
        'PRICE_LEVEL_INEXPENSIVE': '$',
        'PRICE_LEVEL_MODERATE': '$$',
        'PRICE_LEVEL_EXPENSIVE': '$$$',
        'PRICE_LEVEL_VERY_EXPENSIVE': '$$$$'
    };
    return priceLevels[priceLevel] ?? 'Unknown';
}