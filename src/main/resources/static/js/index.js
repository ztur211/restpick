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
    let list = document.getElementById('autocomplete-list');

    if (!list) {
        list = document.createElement('div');
        list.id = 'autocomplete-list';
        list.className = 'autocomplete-list';
        autocompleteWrapper.appendChild(list);
    }

    list.innerHTML = '';

    suggestions.forEach(s => {
        const item = document.createElement('div');
        item.classList.add('autocomplete-item');

        item.innerHTML = `
            <div class="main-text">${s.mainText}</div>
            <div class="secondary-text">${s.secondaryText || ''}</div>
        `;

        item.addEventListener('click', () => onSuggestionSelected(s));
        list.appendChild(item);
    });

    autocompleteWrapper.classList.add('is-open');
}


async function onSuggestionSelected(suggestion) {
    removeActionButton();
    searchInput.value = `${suggestion.mainText}${suggestion.secondaryText ? ", " + suggestion.secondaryText : ""}`.replace(/,\s*/g, ", ");
    clearSuggestions();
    autocompleteWrapper.classList.toggle('is-open', false);

    try {
        const response = await fetch('/resolve-location', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: suggestion.placeId })
        });

        if (!response.ok) throw new Error('Failed to resolve location');

        selectedLocation = await response.json();
        showActionButton();

    } catch (error) {
        console.error('Error resolving location:', error);
        removeActionButton();
    }
    console.log("Selected placeId:", suggestion.placeId);
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
        // Ensure that user clicks on address in autocomplete list
        if (!selectedLocation) {
            alert("Please select an address from the suggestions first.");
            return;
        }

        // Collect all filter values
        const radiusMiles = document.querySelector('input[name="radius"]:checked')?.value || '';
        const cuisine = document.querySelector('input[name="cuisine"]:checked')?.value || '';
        const price = document.querySelector('input[name="price"]:checked')?.value || '';
        const minRating = document.querySelector('input[name="minRating"]:checked')?.value || '';
        const openNow = document.querySelector('input[name="openNow"]:checked')?.value === 'true';

        const requestBody = {
            userAddress: searchInput.value,
            locationRestriction: {
                circle: {
                    center: {
                        latitude: selectedLocation.latitude,
                        longitude: selectedLocation.longitude
                    },
                    radius: radiusMiles ? parseFloat(radiusMiles) : 1609.34 // Default radius
                }
            },
            types: cuisine ? [cuisine] : [],
            priceLevel: price ? [price] : [],
            rating: minRating ? parseFloat(minRating) : null,
            openNow: openNow
        };
        console.log(requestBody);


        try {
            const modal = new bootstrap.Modal(document.getElementById('restaurantModal'));
            document.getElementById('modal-title').textContent = "Finding a restaurant...";
            document.getElementById('modal-body').innerHTML = `
                <div class="text-center py-4">
                    <div class="spinner-border text-primary" role="status"></div>
                    <p class="mt-3">Searching nearby restaurants...</p>
                </div>
            `;
            modal.show();
            const response = await fetch('/pick', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const err = await response.json();

                // Cannot find a restaurant message in modal
                document.getElementById('modal-title').textContent = "No Restaurants Found";
                document.getElementById('modal-body').innerHTML = `
                    <div class="text-center py-4">
                        <p class="mb-2">No restaurants matched your filters.</p>
                        <p class="text-muted">Try widening your radius or lowering your rating/price filters.</p>
                    </div>
                `;
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
    let list = document.getElementById('autocomplete-list');
    if (list) {
        list.innerHTML = '';
        return;
    }

    // If it doesn't exist, create it
    list = document.createElement('div');
    list.id = 'autocomplete-list';
    list.className = 'autocomplete-list';
    autocompleteWrapper.appendChild(list);
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

function renderStars(rating) {
    const fullStars = Math.floor(rating);
    const halfStar = rating % 1 >= 0.5;
    const emptyStars = 5 - fullStars - (halfStar ? 1 : 0);

    return `
        ${'★'.repeat(fullStars)}
        ${halfStar ? '☆' : ''}
        ${'✩'.repeat(emptyStars)}
    `.replace(/\s+/g, '');
}

function showResult(restaurant) {

    if (!restaurant || restaurant.error || !restaurant.name) {
        document.getElementById('modal-title').textContent = "No Restaurants Found";
        document.getElementById('modal-body').innerHTML = `
            <div class="text-center py-4">
                <p class="mb-2">No restaurants matched your filters.</p>
                <p class="text-muted">Try adjusting your search settings.</p>
            </div>
        `;
        return;
    }

    const title = document.getElementById('modal-title');
    const body = document.getElementById('modal-body');
    const stars = renderStars(restaurant.rating);
    const reviewCount = restaurant.ratingCount ?? 0;
    const googleName = restaurant.name.replace("places/", "");
    const googleMapsUrl = `https://www.google.com/maps/place/?q=place_id:${googleName}`;

    title.innerHTML = `
        <a href="${restaurant.websiteUri || googleMapsUrl}" 
        target="_blank" 
        class="restaurant-title-link">
            ${restaurant.displayName}
            <span class="external-icon">↗</span>
        </a>
    `;

    const reviewsUrl = `https://www.google.com/maps/place/?q=place_id:${googleName}&entry=ttu&lrd=${googleName},1`;
    const mapUrl = `/map-image?latitude=${restaurant.latitude}&longitude=${restaurant.longitude}`;
    const directionsUrl = `https://www.google.com/maps/dir/?api=1` + 
        `&origin=${encodeURIComponent(restaurant.originAddress)}` + 
        `&destination=${restaurant.latitude},${restaurant.longitude}`;

    const hasPhotos = Array.isArray(restaurant.photos) && restaurant.photos.length > 0;

    const photoCarousel = hasPhotos
        ? `
            <div id="photoCarousel" class="carousel slide" data-bs-ride="carousel">
                <div class="carousel-inner">
                    ${restaurant.photos.map((p, i) => `
                        <div class="carousel-item ${i === 0 ? 'active' : ''}">
                            <img src="/photo?photoRef=${p}" class="d-block w-100 rounded">
                        </div>
                    `).join('')}
                </div>
                <button class="carousel-control-prev" type="button" data-bs-target="#photoCarousel" data-bs-slide="prev">
                    <span class="carousel-control-prev-icon"></span>
                </button>
                <button class="carousel-control-next" type="button" data-bs-target="#photoCarousel" data-bs-slide="next">
                    <span class="carousel-control-next-icon"></span>
                </button>
            </div>
        `
        : `<p>No photos available</p>`;

    title.innerHTML = `
        <a href="${restaurant.websiteUri || googleMapsUrl}" 
        target="_blank" 
        class="restaurant-title-link">
            ${restaurant.displayName}
            <span class="external-icon">↗</span>
        </a>
    `;

    body.innerHTML = `
        <div class="row equal-row">
            <div class="col-md-8 pb-3">

                <div class="d-flex align-items-center mb-2">
                    <span style="font-size: 1.4rem; font-weight:600;">${restaurant.rating}</span>
                    <span class="ms-2" style="color:#fbbc04; font-size:1.4rem;">${stars}</span>

                    <a href="${reviewsUrl}" 
                       target="_blank" 
                       class="text-muted ms-2"
                       style="font-size:0.95rem; text-decoration: underline;">
                       (${reviewCount} reviews)
                    </a>
                </div>

                <p class="mb-1">
                    <span style="font-size: 1.2rem;">📍</span>
                    ${restaurant.formattedAddress}
                </p>

                <p class="mb-3">
                    <span style="font-size: 1.2rem;">💲</span>
                    ${formatPriceLevel(restaurant.priceLevel)}
                </p>
                <div class="carousel-wrapper">
                    ${photoCarousel}
                </div>

            </div>

            <div class="col-md-4 pb-3">
                <img src="${mapUrl}" alt="Map view" class="img-fluid rounded border map-image">           
            </div>
        </div>
        <div class="row">
            <div class="col-md-12">
                <a href="${directionsUrl}" target="_blank" class="btn btn-success w-100">
                    Get Directions
                </a>
            </div>
        </div>
    `;
}